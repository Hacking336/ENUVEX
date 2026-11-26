package com.jobskills;

import com.jobskills.model.EmployerProfile;
import com.jobskills.model.JobPost;
import com.jobskills.model.SuspiciousListing;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.ModerationStatus;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.SuspiciousListingRepository;
import com.jobskills.repository.UserRepository;
import com.jobskills.service.SuspiciousListingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Rules of {@link SuspiciousListingService}, with the repositories mocked so no
 * database is needed. Each test asserts both that the right rule fired and that
 * the stored explanation names the evidence an administrator can check.
 */
class SuspiciousListingServiceTest {

    private SuspiciousListingRepository flags;
    private JobPostRepository jobPosts;
    private SuspiciousListingService service;

    private EmployerProfile employer;

    @BeforeEach
    void setUp() {
        flags = Mockito.mock(SuspiciousListingRepository.class);
        jobPosts = Mockito.mock(JobPostRepository.class);
        service = new SuspiciousListingService(flags, jobPosts, Mockito.mock(UserRepository.class));

        when(flags.existsByJobPostIdAndTriggeredRuleAndDismissedFalse(anyLong(), anyString()))
            .thenReturn(false);
        when(flags.save(any(SuspiciousListing.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        employer = new EmployerProfile();
        employer.setId(7L);
        employer.setBusinessName("Cebu Trading Corp");
        employer.setContactNumber("09171234567");
    }

    private JobPost job(Long id, String title, String description) {
        JobPost post = new JobPost();
        post.setId(id);
        post.setEmployer(employer);
        post.setJobTitle(title);
        post.setJobDescription(description);
        post.setResponsibilities("Handle counter sales and daily stock counts.");
        post.setJobCategory(JobCategory.SALES);
        post.setMunicipality(Municipality.DAET);
        post.setBarangay("Lahug");
        return post;
    }

    private List<String> rulesFired(List<SuspiciousListing> created) {
        return created.stream().map(SuspiciousListing::getTriggeredRule).toList();
    }

    @Test
    @DisplayName("normalize lowercases, drops punctuation and collapses spacing")
    void normalize() {
        assertThat(SuspiciousListingService.normalize("  Cashier (Full-Time)!!  "))
            .isEqualTo("cashier full time");
        assertThat(SuspiciousListingService.normalize(null)).isEmpty();
    }

    @Test
    @DisplayName("Rule A: same employer, same title raises DUPLICATE_TITLE and names the other posting")
    void duplicateTitle() {
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of(job(2L, "Cashier", "Some other work.")));
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        List<SuspiciousListing> created = service.evaluate(job(1L, "cashier", "Serve customers at the counter daily."));

        assertThat(rulesFired(created)).contains(SuspiciousListingService.RULE_DUPLICATE_TITLE);
        SuspiciousListing flag = created.stream()
            .filter(item -> item.getTriggeredRule().equals(SuspiciousListingService.RULE_DUPLICATE_TITLE))
            .findFirst().orElseThrow();
        assertThat(flag.getFlaggedReason()).contains("job #2");
        assertThat(flag.getStatus()).isEqualTo(ModerationStatus.PENDING_REVIEW);
        assertThat(flag.isDismissed()).isFalse();
    }

    @Test
    @DisplayName("Rule A: an identical description raises DUPLICATE_DESCRIPTION")
    void duplicateDescription() {
        String description = "Operate the cash register, issue receipts and reconcile the drawer every evening.";
        when(jobPosts.findByEmployerId(7L))
            .thenReturn(List.of(job(2L, "Sales Associate", description)));
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        List<SuspiciousListing> created = service.evaluate(job(1L, "Store Helper", description));

        assertThat(rulesFired(created)).contains(SuspiciousListingService.RULE_DUPLICATE_DESCRIPTION);
        assertThat(created.stream()
            .filter(item -> item.getTriggeredRule().equals(SuspiciousListingService.RULE_DUPLICATE_DESCRIPTION))
            .findFirst().orElseThrow().getFlaggedReason()).contains("identical");
    }

    @Test
    @DisplayName("Rule A: same title in the same barangay raises SAME_TITLE_AND_LOCATION")
    void sameTitleAndLocation() {
        JobPost other = job(2L, "Cashier", "Counter duty with evening shifts.");
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of(other));
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        List<SuspiciousListing> created = service.evaluate(job(1L, "Cashier", "Counter duty with morning shifts."));

        assertThat(rulesFired(created)).contains(SuspiciousListingService.RULE_SAME_TITLE_AND_LOCATION);
        assertThat(created.stream()
            .filter(item -> item.getTriggeredRule().equals(SuspiciousListingService.RULE_SAME_TITLE_AND_LOCATION))
            .findFirst().orElseThrow().getFlaggedReason()).contains("Lahug");
    }

    @Test
    @DisplayName("Rule B: missing responsibilities and description are listed in one explanation")
    void missingInformation() {
        JobPost incomplete = job(1L, "Cashier", "short");
        incomplete.setResponsibilities(null);
        incomplete.setMunicipality(null);
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        List<SuspiciousListing> created = service.evaluate(incomplete);

        assertThat(rulesFired(created)).contains(SuspiciousListingService.RULE_MISSING_INFORMATION);
        String reason = created.stream()
            .filter(item -> item.getTriggeredRule().equals(SuspiciousListingService.RULE_MISSING_INFORMATION))
            .findFirst().orElseThrow().getFlaggedReason();
        assertThat(reason).contains("job description").contains("responsibilities").contains("municipality");
    }

    @Test
    @DisplayName("Rule B: optional fields such as skills or salary are never required")
    void optionalFieldsAreNotFlagged() {
        JobPost post = job(1L, "Cashier", "Serve walk-in customers, handle payments and balance the counter till.");
        post.setRequiredSkills(null);
        post.setSalaryMin(null);
        post.setBarangay(null);
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        List<SuspiciousListing> created = service.evaluate(post);

        assertThat(rulesFired(created))
            .doesNotContain(SuspiciousListingService.RULE_MISSING_INFORMATION);
    }

    @Test
    @DisplayName("Rule C: details repeating a rejected posting raise REPEATED_REJECTED_LISTING")
    void repeatedRejectedListing() {
        JobPost rejected = job(5L, "Encoder", "Encode daily sales reports and file them with the branch head.");
        rejected.setModerationStatus(ModerationStatus.REJECTED);
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED))
            .thenReturn(List.of(rejected));

        List<SuspiciousListing> created = service.evaluate(
            job(1L, "Encoder", "Encode daily sales reports and file them with the branch head."));

        assertThat(rulesFired(created)).contains(SuspiciousListingService.RULE_REPEATED_REJECTED);
        assertThat(created.stream()
            .filter(item -> item.getTriggeredRule().equals(SuspiciousListingService.RULE_REPEATED_REJECTED))
            .findFirst().orElseThrow().getFlaggedReason()).contains("job #5").contains("rejected");
    }

    @Test
    @DisplayName("Rule D: a request for payment raises SUSPICIOUS_PAYMENT_REQUEST and quotes the phrase")
    void paymentRequest() {
        JobPost post = job(1L, "Warehouse Helper",
            "Apply now and pay the processing fee of 500 pesos via GCash to reserve your slot.");
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        List<SuspiciousListing> created = service.evaluate(post);

        assertThat(rulesFired(created)).contains(SuspiciousListingService.RULE_PAYMENT_REQUEST);
        assertThat(created.stream()
            .filter(item -> item.getTriggeredRule().equals(SuspiciousListingService.RULE_PAYMENT_REQUEST))
            .findFirst().orElseThrow().getFlaggedReason())
            .contains("processing fee").contains("gcash");
    }

    @Test
    @DisplayName("Ordinary salary wording and contact details are not flagged")
    void cleanPostingProducesNoFlags() {
        JobPost post = job(1L, "Accounting Staff",
            "Prepare monthly financial statements and support the annual audit. Salary is negotiable; "
                + "call the branch at 09171234567 for questions about the role.");
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        assertThat(service.evaluate(post)).isEmpty();
    }

    @Test
    @DisplayName("A rule that already has a live flag is not flagged again on resubmission")
    void dedupeOpenFlag() {
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());
        when(flags.existsByJobPostIdAndTriggeredRuleAndDismissedFalse(
            1L, SuspiciousListingService.RULE_PAYMENT_REQUEST)).thenReturn(true);

        List<SuspiciousListing> created = service.evaluate(
            job(1L, "Helper", "Send payment to secure your application slot today."));

        assertThat(rulesFired(created)).doesNotContain(SuspiciousListingService.RULE_PAYMENT_REQUEST);
        verify(flags, never()).save(any(SuspiciousListing.class));
    }

    @Test
    @DisplayName("Detection never writes a moderation status onto the posting itself")
    void detectionDoesNotPunish() {
        JobPost post = job(1L, "Helper", "Send payment to secure your application slot today.");
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        service.evaluate(post);

        assertThat(post.getModerationStatus()).isEqualTo(ModerationStatus.PENDING_REVIEW);
        verify(jobPosts, never()).save(any(JobPost.class));
    }

    @Test
    @DisplayName("Every stored flag names the job it belongs to")
    void flagsReferenceTheirPosting() {
        when(jobPosts.findByEmployerId(7L)).thenReturn(List.of());
        when(jobPosts.findByEmployerIdAndModerationStatus(7L, ModerationStatus.REJECTED)).thenReturn(List.of());

        JobPost post = job(1L, "Helper", "Send payment to secure your application slot today.");
        service.evaluate(post);

        ArgumentCaptor<SuspiciousListing> captor = ArgumentCaptor.forClass(SuspiciousListing.class);
        verify(flags).save(captor.capture());
        assertThat(captor.getValue().getJobPost()).isSameAs(post);
        assertThat(captor.getValue().getFlaggedReason()).isNotBlank();
        assertThat(new ArrayList<>(rulesFired(List.of(captor.getValue()))))
            .containsExactly(SuspiciousListingService.RULE_PAYMENT_REQUEST);
    }
}
