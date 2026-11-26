package com.jobskills.service;

import com.jobskills.model.EmployerProfile;
import com.jobskills.model.JobPost;
import com.jobskills.model.SuspiciousListing;
import com.jobskills.model.User;
import com.jobskills.model.enums.ModerationStatus;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.SuspiciousListingRepository;
import com.jobskills.repository.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Deterministic, explainable checks that flag a job posting for human review.
 *
 * <p>Every rule reads only data already stored in this database. There is no
 * scoring model, no external service and no artificial fraud percentage: a flag
 * states which rule fired and what it found, so an administrator can judge it.
 * A flag never suspends an account, rejects a posting or changes any status on
 * its own.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@code DUPLICATE_TITLE} – the same employer already has a live posting
 *       with the same title.</li>
 *   <li>{@code DUPLICATE_DESCRIPTION} – the same employer already has a live
 *       posting with an identical, normalised description.</li>
 *   <li>{@code SAME_TITLE_AND_LOCATION} – the same title repeated in the same
 *       barangay for one employer.</li>
 *   <li>{@code MISSING_REQUIRED_INFORMATION} – a field the posting form requires
 *       is empty or too short to describe the role.</li>
 *   <li>{@code REPEATED_REJECTED_LISTING} – the details match a posting this
 *       employer already had rejected.</li>
 *   <li>{@code SUSPICIOUS_PAYMENT_REQUEST} – the description asks applicants for
 *       money, which no legitimate hiring process does.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class SuspiciousListingService {

    public static final String RULE_DUPLICATE_TITLE = "DUPLICATE_TITLE";
    public static final String RULE_DUPLICATE_DESCRIPTION = "DUPLICATE_DESCRIPTION";
    public static final String RULE_SAME_TITLE_AND_LOCATION = "SAME_TITLE_AND_LOCATION";
    public static final String RULE_MISSING_INFORMATION = "MISSING_REQUIRED_INFORMATION";
    public static final String RULE_REPEATED_REJECTED = "REPEATED_REJECTED_LISTING";
    public static final String RULE_PAYMENT_REQUEST = "SUSPICIOUS_PAYMENT_REQUEST";

    /**
     * Phrases that only appear when money is being asked of the applicant.
     * Deliberately narrow: ordinary salary talk or a legitimate fee paid by the
     * employer is not on this list.
     */
    private static final List<String> PAYMENT_PHRASES = List.of(
        "application fee", "processing fee", "registration fee", "training fee",
        "placement fee", "send money", "send payment", "pay first",
        "deposit to", "cash deposit", "gcash", "palawan express",
        "bank transfer to", "pay via gcash", "payment is required", "fee is required"
    );

    /** A description shorter than this does not describe the work. */
    private static final int MIN_DESCRIPTION_LENGTH = 40;

    /** A responsibilities list shorter than this does not list any duty. */
    private static final int MIN_RESPONSIBILITIES_LENGTH = 15;

    private final SuspiciousListingRepository suspiciousListingRepository;
    private final JobPostRepository jobPostRepository;
    private final UserRepository userRepository;

    /**
     * Runs every rule against one posting and stores a flag for each rule that
     * fires. Safe to call on every submit and edit: a rule that already has a
     * live flag is not duplicated.
     *
     * @return the flags created by this call (never null)
     */
    @Transactional
    public List<SuspiciousListing> evaluate(JobPost job) {
        List<SuspiciousListing> created = new ArrayList<>();
        if (job == null || job.getId() == null) {
            return created;
        }

        checkDuplicates(job, created);
        checkMissingInformation(job, created);
        checkRepeatedRejection(job, created);
        checkPaymentRequest(job, created);
        return created;
    }

    /** True when this exact rule already has an unresolved flag on the post. */
    private boolean alreadyFlagged(JobPost job, String rule) {
        return suspiciousListingRepository.existsByJobPostIdAndTriggeredRuleAndDismissedFalse(job.getId(), rule);
    }

    private void flag(JobPost job, String rule, String reason, List<SuspiciousListing> created) {
        if (alreadyFlagged(job, rule)) {
            return;
        }
        SuspiciousListing listing = SuspiciousListing.builder()
            .jobPost(job)
            .triggeredRule(rule)
            .flaggedReason(reason)
            .status(ModerationStatus.PENDING_REVIEW)
            .dismissed(false)
            .build();
        created.add(suspiciousListingRepository.save(listing));
    }

    /* ---------- Rule A: duplicate listings ------------------------------- */

    private void checkDuplicates(JobPost job, List<SuspiciousListing> created) {
        EmployerProfile employer = job.getEmployer();
        if (employer == null || employer.getId() == null) {
            return;
        }

        String title = normalize(job.getJobTitle());
        String description = normalize(job.getJobDescription());
        String barangay = normalize(job.getBarangay());

        List<JobPost> siblings = jobPostRepository.findByEmployerId(employer.getId()).stream()
            .filter(other -> !other.getId().equals(job.getId()))
            .collect(Collectors.toList());

        for (JobPost other : siblings) {
            if (!title.isEmpty() && title.equals(normalize(other.getJobTitle()))) {
                flag(job, RULE_DUPLICATE_TITLE,
                    "This employer already has another posting titled \"" + job.getJobTitle()
                        + "\" (job #" + other.getId() + ").", created);
                break;
            }
        }

        if (description.length() >= MIN_DESCRIPTION_LENGTH) {
            for (JobPost other : siblings) {
                if (description.equals(normalize(other.getJobDescription()))) {
                    flag(job, RULE_DUPLICATE_DESCRIPTION,
                        "The job description is identical to job #" + other.getId()
                            + " from the same employer.", created);
                    break;
                }
            }
        }

        if (!title.isEmpty() && !barangay.isEmpty()) {
            for (JobPost other : siblings) {
                if (title.equals(normalize(other.getJobTitle()))
                    && barangay.equals(normalize(other.getBarangay()))) {
                    flag(job, RULE_SAME_TITLE_AND_LOCATION,
                        "The same title is posted more than once in barangay " + job.getBarangay()
                            + " (other posting: job #" + other.getId() + ").", created);
                    break;
                }
            }
        }
    }

    /* ---------- Rule B: missing required information --------------------- */

    private void checkMissingInformation(JobPost job, List<SuspiciousListing> created) {
        List<String> problems = new ArrayList<>();

        if (isBlank(job.getJobTitle())) {
            problems.add("position title is empty");
        }
        if (isBlank(job.getJobDescription()) || job.getJobDescription().trim().length() < MIN_DESCRIPTION_LENGTH) {
            problems.add("job description is missing or too short to describe the work");
        }
        if (isBlank(job.getResponsibilities()) || job.getResponsibilities().trim().length() < MIN_RESPONSIBILITIES_LENGTH) {
            problems.add("key responsibilities are missing");
        }
        if (job.getJobCategory() == null) {
            problems.add("job category is not set");
        }
        if (job.getMunicipality() == null) {
            problems.add("municipality is not set");
        }

        EmployerProfile employer = job.getEmployer();
        if (employer == null || isBlank(employer.getBusinessName())) {
            problems.add("employer business name is missing");
        } else if (isBlank(employer.getContactNumber())) {
            problems.add("employer has no contact number on file");
        }

        if (!problems.isEmpty()) {
            flag(job, RULE_MISSING_INFORMATION,
                "Required posting information is incomplete: " + String.join("; ", problems) + ".", created);
        }
    }

    /* ---------- Rule C: repeated rejected listings ----------------------- */

    private void checkRepeatedRejection(JobPost job, List<SuspiciousListing> created) {
        EmployerProfile employer = job.getEmployer();
        if (employer == null || employer.getId() == null) {
            return;
        }

        String title = normalize(job.getJobTitle());
        String description = normalize(job.getJobDescription());
        if (title.isEmpty()) {
            return;
        }

        List<JobPost> rejected = jobPostRepository.findByEmployerIdAndModerationStatus(
            employer.getId(), ModerationStatus.REJECTED);

        for (JobPost previous : rejected) {
            if (previous.getId().equals(job.getId())) {
                continue;
            }
            boolean sameTitle = title.equals(normalize(previous.getJobTitle()));
            boolean sameDescription = description.length() >= MIN_DESCRIPTION_LENGTH
                && description.equals(normalize(previous.getJobDescription()));
            if (sameTitle || sameDescription) {
                flag(job, RULE_REPEATED_REJECTED,
                    "These details repeat job #" + previous.getId()
                        + ", which was already rejected for this employer"
                        + (sameDescription ? " (identical description)" : " (same title)") + ".", created);
                return;
            }
        }
    }

    /* ---------- Rule D: suspicious payment wording ----------------------- */

    private void checkPaymentRequest(JobPost job, List<SuspiciousListing> created) {
        String text = normalize(String.join(" ",
            safe(job.getJobTitle()),
            safe(job.getJobDescription()),
            safe(job.getResponsibilities())));

        if (text.isEmpty()) {
            return;
        }

        List<String> hits = PAYMENT_PHRASES.stream().filter(text::contains).collect(Collectors.toList());
        if (hits.isEmpty()) {
            return;
        }

        flag(job, RULE_PAYMENT_REQUEST,
            "The posting text asks applicants for payment (matched: "
                + hits.stream().map(hit -> "\"" + hit + "\"").collect(Collectors.joining(", "))
                + "). A legitimate employer does not charge an applicant.", created);
    }

    /* ---------- helpers -------------------------------------------------- */

    /** Lowercases, collapses whitespace and drops punctuation for comparison. */
    public static String normalize(String value) {
        if (value == null) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT)
            .replaceAll("[^a-z0-9\\s]", " ")
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    /** Notifies every active administrator that a listing needs review. */
    @Transactional
    public void notifyAdmins(List<SuspiciousListing> flags, NotificationService notificationService) {
        if (flags == null || flags.isEmpty() || notificationService == null) {
            return;
        }
        List<User> admins = userRepository.findAll().stream()
            .filter(user -> user.getUserType() == com.jobskills.model.enums.UserType.ADMIN)
            .filter(User::isActive)
            .collect(Collectors.toList());

        for (SuspiciousListing flag : flags) {
            for (User admin : admins) {
                notificationService.notify(admin, NotificationService.SUSPICIOUS_LISTING_FLAGGED,
                    "Listing flagged for review",
                    flag.getJobPost().getJobTitle() + " triggered " + flag.getTriggeredRule()
                        + ". Review the flag before taking any action.",
                    "/admin/suspicious-listings.html", "SUSPICIOUS_LISTING", flag.getId(),
                    "suspicious-" + flag.getId() + "-admin-" + admin.getId());
            }
        }
    }
}
