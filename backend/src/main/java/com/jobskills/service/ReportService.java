package com.jobskills.service;

import com.jobskills.model.Report;
import com.jobskills.model.User;
import com.jobskills.model.enums.ReportCategory;
import com.jobskills.model.enums.ReportStatus;
import com.jobskills.repository.EmployerProfileRepository;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.ReportRepository;
import com.jobskills.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Submission of reports about a posting, employer or account.
 *
 * <p>The reporter is always the authenticated account, never a value sent by
 * the browser, and a reporter may only read back their own reports. Reporting a
 * target never changes that target: no suspension, no removal, no automatic
 * decision.
 */
@Service
@RequiredArgsConstructor
public class ReportService {

    public static final String TARGET_JOB_POST = "JOB_POST";
    public static final String TARGET_EMPLOYER = "EMPLOYER";
    public static final String TARGET_USER = "USER";

    private static final int MIN_DESCRIPTION_LENGTH = 10;

    private final ReportRepository reportRepository;
    private final JobPostRepository jobPostRepository;
    private final EmployerProfileRepository employerProfileRepository;
    private final UserRepository userRepository;

    /**
     * Files a report on behalf of {@code reporter}.
     *
     * @throws IllegalArgumentException when the input is invalid, the target does
     *                                  not exist, or this reporter already
     *                                  reported the same target
     */
    @Transactional
    public Report submit(User reporter, String targetType, Long targetId,
                         ReportCategory category, String description) {
        if (reporter == null) {
            throw new IllegalArgumentException("You must be signed in to submit a report.");
        }
        String type = targetType == null ? "" : targetType.trim().toUpperCase();
        if (!TARGET_JOB_POST.equals(type) && !TARGET_EMPLOYER.equals(type) && !TARGET_USER.equals(type)) {
            throw new IllegalArgumentException("Choose what you are reporting.");
        }
        if (targetId == null) {
            throw new IllegalArgumentException("The report is missing its target.");
        }
        if (category == null) {
            throw new IllegalArgumentException("Choose a reason for the report.");
        }
        if (description == null || description.trim().length() < MIN_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException(
                "Describe the problem in at least " + MIN_DESCRIPTION_LENGTH + " characters.");
        }
        if (!targetExists(type, targetId)) {
            throw new IllegalArgumentException("The reported item could not be found.");
        }
        if (reportRepository.existsByReporterIdAndReportedTargetTypeAndReportedTargetId(
                reporter.getId(), type, targetId)) {
            throw new IllegalArgumentException("You have already reported this item. Our team still has it open.");
        }

        boolean duplicateOfAnotherReport = !reportRepository
            .findByReportedTargetTypeAndReportedTargetIdOrderByCreatedAtDesc(type, targetId).isEmpty();

        Report report = new Report();
        report.setReporter(reporter);
        report.setReportedTargetType(type);
        report.setReportedTargetId(targetId);
        report.setCategory(category);
        report.setDescription(description.trim());
        report.setStatus(ReportStatus.OPEN);
        report.setDuplicate(duplicateOfAnotherReport);
        return reportRepository.save(report);
    }

    /** Only the reporter's own reports; identity is never taken from a request. */
    public List<Report> myReports(User reporter) {
        if (reporter == null) {
            throw new IllegalArgumentException("You must be signed in.");
        }
        return reportRepository.findByReporterIdOrderByCreatedAtDesc(reporter.getId());
    }

    private boolean targetExists(String type, Long targetId) {
        return switch (type) {
            case TARGET_JOB_POST -> jobPostRepository.existsById(targetId);
            case TARGET_EMPLOYER -> employerProfileRepository.findById(targetId).isPresent()
                || employerProfileRepository.findByUserId(targetId).isPresent();
            case TARGET_USER -> userRepository.findById(targetId).isPresent();
            default -> false;
        };
    }
}
