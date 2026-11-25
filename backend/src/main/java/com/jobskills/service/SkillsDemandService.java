package com.jobskills.service;

import com.jobskills.dto.SkillDemandDto;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.JobSeekerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * The one place that turns stored job requirements and profile skills into
 * demand numbers. Both the seeker pages and the admin analytics read from here,
 * so the two can never disagree.
 *
 * Skills are not duplicated into a table: the existing comma-separated fields
 * are parsed with MatchingEngine.splitSkills and keyed with
 * MatchingEngine.normalizeSkill, which is also what the matching engine uses.
 * A skill is therefore one entry regardless of case, spacing or ordering.
 */
@Service
@RequiredArgsConstructor
public class SkillsDemandService {

    private final JobPostRepository jobPostRepository;
    private final JobSeekerProfileRepository seekerRepository;

    private static final Map<JobCategory, String> CATEGORY_LABELS = new EnumMap<>(JobCategory.class);
    private static final Map<EmploymentType, String> TYPE_LABELS = new EnumMap<>(EmploymentType.class);

    static {
        CATEGORY_LABELS.put(JobCategory.RETAIL_STORE, "Retail / Store");
        CATEGORY_LABELS.put(JobCategory.SALES, "Sales");
        CATEGORY_LABELS.put(JobCategory.FOOD_SERVICE_RESTAURANT, "Food Service / Restaurant");
        CATEGORY_LABELS.put(JobCategory.HOSPITALITY_TOURISM, "Hospitality / Tourism");
        CATEGORY_LABELS.put(JobCategory.OFFICE_ADMINISTRATION, "Office / Administration");
        CATEGORY_LABELS.put(JobCategory.ACCOUNTING_FINANCE, "Accounting / Finance");
        CATEGORY_LABELS.put(JobCategory.HEALTHCARE, "Healthcare");
        CATEGORY_LABELS.put(JobCategory.EDUCATION_TUTORING, "Education / Tutoring");
        CATEGORY_LABELS.put(JobCategory.CONSTRUCTION_SKILLED_TRADES, "Construction / Skilled Trades");
        CATEGORY_LABELS.put(JobCategory.AGRICULTURE, "Agriculture");
        CATEGORY_LABELS.put(JobCategory.FISHERIES, "Fisheries");
        CATEGORY_LABELS.put(JobCategory.MANUFACTURING, "Manufacturing");
        CATEGORY_LABELS.put(JobCategory.LOGISTICS_DELIVERY, "Logistics / Delivery");
        CATEGORY_LABELS.put(JobCategory.TRANSPORTATION, "Transportation");
        CATEGORY_LABELS.put(JobCategory.AUTOMOTIVE_MECHANICAL, "Automotive / Mechanical");
        CATEGORY_LABELS.put(JobCategory.CUSTOMER_SERVICE, "Customer Service");
        CATEGORY_LABELS.put(JobCategory.SECURITY, "Security");
        CATEGORY_LABELS.put(JobCategory.GENERAL_LABOR, "General Labor");
        CATEGORY_LABELS.put(JobCategory.IT_TECHNOLOGY, "IT / Technology");
        CATEGORY_LABELS.put(JobCategory.OTHER, "Other");

        TYPE_LABELS.put(EmploymentType.FULL_TIME, "Full-time");
        TYPE_LABELS.put(EmploymentType.PART_TIME, "Part-time");
        TYPE_LABELS.put(EmploymentType.CONTRACT, "Contract");
        TYPE_LABELS.put(EmploymentType.TEMPORARY, "Temporary");
    }

    public static String categoryLabel(JobCategory category) {
        if (category == null) return "Uncategorized";
        return CATEGORY_LABELS.getOrDefault(category, titleCase(category.name()));
    }

    public static String typeLabel(EmploymentType type) {
        if (type == null) return "Not stated";
        return TYPE_LABELS.getOrDefault(type, titleCase(type.name()));
    }

    public static String municipalityLabel(Municipality municipality) {
        if (municipality == null) return "Not stated";
        return titleCase(municipality.name());
    }

    private static String titleCase(String raw) {
        String lower = raw.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    @Transactional(readOnly = true)
    public List<JobPost> activeJobs() {
        return jobPostRepository.findByIsActiveTrue();
    }

    /** Applies the shared demand filters. A null filter means "no restriction". */
    public List<JobPost> filter(List<JobPost> jobs, Municipality municipality, String barangay,
                                EmploymentType employmentType, JobCategory category, Integer days) {
        String wantedBarangay = barangay == null ? "" : barangay.trim().toLowerCase(Locale.ROOT);
        LocalDateTime cutoff = days == null || days <= 0 ? null : LocalDateTime.now().minusDays(days);
        List<JobPost> result = new ArrayList<>();
        for (JobPost job : jobs) {
            if (municipality != null && job.getMunicipality() != municipality) continue;
            if (!wantedBarangay.isEmpty()) {
                String jobBarangay = job.getBarangay() == null ? "" : job.getBarangay().trim().toLowerCase(Locale.ROOT);
                if (!wantedBarangay.equals(jobBarangay)) continue;
            }
            if (employmentType != null && job.getEmploymentType() != employmentType) continue;
            if (category != null && job.getJobCategory() != category) continue;
            if (cutoff != null && (job.getCreatedAt() == null || job.getCreatedAt().isBefore(cutoff))) continue;
            result.add(job);
        }
        return result;
    }

    /** Skill key -> the active jobs that require it, in the given set. */
    public Map<String, List<JobPost>> bySkill(List<JobPost> jobs) {
        Map<String, List<JobPost>> grouped = new LinkedHashMap<>();
        for (JobPost job : jobs) {
            for (String skill : MatchingEngine.splitSkills(job.getRequiredSkills())) {
                String key = MatchingEngine.normalizeSkill(skill);
                if (key.isEmpty()) continue;
                grouped.computeIfAbsent(key, ignored -> new ArrayList<>()).add(job);
            }
        }
        return grouped;
    }

    /** How many profiles record each normalized skill. Aggregate only. */
    @Transactional(readOnly = true)
    public Map<String, Integer> seekerSupply() {
        Map<String, Integer> supply = new HashMap<>();
        for (JobSeekerProfile profile : seekerRepository.findAll()) {
            Set<String> seen = new HashSet<>();
            for (String skill : MatchingEngine.splitSkills(profile.getSkills())) {
                String key = MatchingEngine.normalizeSkill(skill);
                if (!key.isEmpty() && seen.add(key)) {
                    supply.merge(key, 1, Integer::sum);
                }
            }
        }
        return supply;
    }

    /**
     * The spelling used for one skill key: the one most postings used, so the
     * interface shows the employers' own wording rather than a guess. Only
     * spellings that normalize to the given key are counted, otherwise a job
     * listing several skills would name every one of them after the same skill.
     */
    public String displayName(String key, List<JobPost> jobs) {
        Map<String, Integer> spellings = new LinkedHashMap<>();
        for (JobPost job : jobs) {
            for (String skill : MatchingEngine.splitSkills(job.getRequiredSkills())) {
                if (MatchingEngine.normalizeSkill(skill).equals(key)) {
                    spellings.merge(skill.trim(), 1, Integer::sum);
                }
            }
        }
        return spellings.entrySet().stream()
            .max(Comparator.<Map.Entry<String, Integer>>comparingInt(Map.Entry::getValue)
                .thenComparing(Map.Entry::getKey))
            .map(Map.Entry::getKey)
            .orElse("");
    }

    /** Demand rows for a set of postings, sorted by posting count. */
    public List<SkillDemandDto> summarize(List<JobPost> jobs, Map<String, Integer> supply, int limit) {
        Map<String, List<JobPost>> grouped = bySkill(jobs);
        int jobsWithSkills = 0;
        for (JobPost job : jobs) {
            if (!MatchingEngine.splitSkills(job.getRequiredSkills()).isEmpty()) jobsWithSkills++;
        }

        List<SkillDemandDto> rows = new ArrayList<>();
        for (Map.Entry<String, List<JobPost>> entry : grouped.entrySet()) {
            List<JobPost> requiring = entry.getValue();
            SkillDemandDto row = new SkillDemandDto();
            row.setSkillKey(entry.getKey());
            row.setSkill(displayName(entry.getKey(), requiring));
            row.setJobCount(requiring.size());
            row.setSharePercent(jobsWithSkills == 0 ? null
                : round1(requiring.size() * 100.0 / jobsWithSkills));
            row.setSeekerCount(supply.getOrDefault(entry.getKey(), 0));

            Map<String, Integer> municipalities = new LinkedHashMap<>();
            Map<String, Integer> types = new LinkedHashMap<>();
            Map<JobCategory, Integer> categories = new EnumMap<>(JobCategory.class);
            for (JobPost job : requiring) {
                municipalities.merge(municipalityLabel(job.getMunicipality()), 1, Integer::sum);
                types.merge(typeLabel(job.getEmploymentType()), 1, Integer::sum);
                if (job.getJobCategory() != null) {
                    categories.merge(job.getJobCategory(), 1, Integer::sum);
                }
            }
            row.setByMunicipality(municipalities);
            row.setByEmploymentType(types);
            categories.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(best -> {
                    row.setCommonCategory(best.getKey().name());
                    row.setCommonCategoryLabel(categoryLabel(best.getKey()));
                });
            rows.add(row);
        }

        rows.sort(Comparator.comparingInt(SkillDemandDto::getJobCount).reversed()
            .thenComparing(SkillDemandDto::getSkill, String.CASE_INSENSITIVE_ORDER));
        if (limit > 0 && rows.size() > limit) {
            return new ArrayList<>(rows.subList(0, limit));
        }
        return rows;
    }

    /** Municipality or employment-type split for one skill's postings. */
    public Map<String, Integer> breakdown(List<JobPost> jobs, boolean municipality) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if (jobs == null) return result;
        for (JobPost job : jobs) {
            result.merge(municipality ? municipalityLabel(job.getMunicipality())
                : typeLabel(job.getEmploymentType()), 1, Integer::sum);
        }
        return result;
    }

    public int jobsWithSkills(List<JobPost> jobs) {
        int count = 0;
        for (JobPost job : jobs) {
            if (!MatchingEngine.splitSkills(job.getRequiredSkills()).isEmpty()) count++;
        }
        return count;
    }

    static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
