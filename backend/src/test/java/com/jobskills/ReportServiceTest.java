package com.jobskills;

import com.jobskills.model.JobPost;
import com.jobskills.model.Report;
import com.jobskills.model.User;
import com.jobskills.model.enums.ReportCategory;
import com.jobskills.model.enums.ReportStatus;
import com.jobskills.repository.EmployerProfileRepository;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.ReportRepository;
import com.jobskills.repository.UserRepository;
import com.jobskills.service.ReportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Report submission rules: required input, an existing target, one report per
 * reporter and target, and no side effect on the reported account.
 */
class ReportServiceTest {

    private ReportRepository reports;
    private JobPostRepository jobPosts;
    private ReportService service;

    private User reporter;

    @BeforeEach
    void setUp() {
        reports = Mockito.mock(ReportRepository.class);
        jobPosts = Mockito.mock(JobPostRepository.class);
        service = new ReportService(reports, jobPosts,
            Mockito.mock(EmployerProfileRepository.class), Mockito.mock(UserRepository.class));

        when(reports.save(any(Report.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reports.existsByReporterIdAndReportedTargetTypeAndReportedTargetId(any(), any(), any()))
            .thenReturn(false);
        when(reports.findByReportedTargetTypeAndReportedTargetIdOrderByCreatedAtDesc(any(), any()))
            .thenReturn(List.of());
        when(jobPosts.existsById(1L)).thenReturn(true);

        reporter = new User();
        reporter.setId(42L);
        reporter.setEmail("seeker@example.com");
    }

    private Report submit(String type, Long targetId, ReportCategory category, String description) {
        return service.submit(reporter, type, targetId, category, description);
    }

    @Test
    @DisplayName("A valid report is stored OPEN, without any administrative decision")
    void validReport() {
        Report report = submit("JOB_POST", 1L, ReportCategory.FRAUD_OR_SCAM,
            "They asked me to send 500 pesos before an interview.");

        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
        assertThat(report.getReporter()).isSameAs(reporter);
        assertThat(report.getReportedTargetType()).isEqualTo("JOB_POST");
        assertThat(report.getReportedTargetId()).isEqualTo(1L);
        assertThat(report.getResolvedAt()).isNull();
        assertThat(report.getResolvedByAdminId()).isNull();
        assertThat(report.isDuplicate()).isFalse();
        verify(jobPosts, never()).save(any(JobPost.class));
    }

    @Test
    @DisplayName("A description that says nothing is rejected")
    void blankDescriptionRejected() {
        assertThatThrownBy(() -> submit("JOB_POST", 1L, ReportCategory.OTHER, "  bad  "))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("at least");
        verify(reports, never()).save(any(Report.class));
    }

    @Test
    @DisplayName("A missing reason or target is rejected")
    void missingReasonOrTarget() {
        assertThatThrownBy(() -> submit("JOB_POST", 1L, null, "They asked me to pay a fee."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("reason");
        assertThatThrownBy(() -> submit("JOB_POST", null, ReportCategory.OTHER, "They asked me to pay a fee."))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> submit("NOT_A_TARGET", 1L, ReportCategory.OTHER, "They asked me to pay a fee."))
            .isInstanceOf(IllegalArgumentException.class);
        verify(reports, never()).save(any(Report.class));
    }

    @Test
    @DisplayName("Reporting something that does not exist is rejected")
    void unknownTargetRejected() {
        assertThatThrownBy(() -> submit("JOB_POST", 99L, ReportCategory.OTHER, "This listing looks fake."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("could not be found");
        verify(reports, never()).save(any(Report.class));
    }

    @Test
    @DisplayName("The same reporter cannot file the same report twice")
    void duplicateReporterRejected() {
        when(reports.existsByReporterIdAndReportedTargetTypeAndReportedTargetId(42L, "JOB_POST", 1L))
            .thenReturn(true);

        assertThatThrownBy(() -> submit("JOB_POST", 1L, ReportCategory.OTHER, "Still asking me for a fee."))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("already reported");
        verify(reports, never()).save(any(Report.class));
    }

    @Test
    @DisplayName("A second report from someone else is marked as a duplicate, not blocked")
    void duplicateByAnotherReporterIsMarked() {
        Report earlier = new Report();
        earlier.setId(3L);
        when(reports.findByReportedTargetTypeAndReportedTargetIdOrderByCreatedAtDesc("JOB_POST", 1L))
            .thenReturn(List.of(earlier));

        Report report = submit("JOB_POST", 1L, ReportCategory.FAKE_EMPLOYER,
            "Same fake address was used on another listing.");

        assertThat(report.isDuplicate()).isTrue();
        assertThat(report.getStatus()).isEqualTo(ReportStatus.OPEN);
    }

    @Test
    @DisplayName("Only the reporter's own reports are returned")
    void myReportsScopedToReporter() {
        Report own = new Report();
        own.setId(9L);
        when(reports.findByReporterIdOrderByCreatedAtDesc(42L)).thenReturn(List.of(own));

        assertThat(service.myReports(reporter)).containsExactly(own);
        assertThatThrownBy(() -> service.myReports(null)).isInstanceOf(IllegalArgumentException.class);
    }
}
