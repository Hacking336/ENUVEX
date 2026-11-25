package com.jobskills.service;

import com.jobskills.dto.MatchCriterionDto;
import com.jobskills.dto.MatchResultDto;
import com.jobskills.dto.ProfileCompletenessDto;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.Municipality;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single, deterministic matching engine used by job recommendations, job
 * search, employer applicant sorting and admin analytics.
 *
 * It is a transparent weighted score, not AI:
 *
 *   skills            40
 *   location          25
 *   employment type   15
 *   experience        10
 *   salary            10
 *
 * A criterion that cannot be judged from the stored data (a job with no listed
 * skills, a salary the employer did not disclose, a seeker with no employment
 * preference) is marked unavailable and removed from the denominator:
 *
 *   final % = earned points / applicable weight * 100
 *
 * That way missing data never counts as a mismatch and never hands out free
 * points. The same instance is the only place a score is calculated, so the
 * seeker's list, the employer's applicant list and the admin statistics always
 * agree.
 */
@Service
public class MatchingEngine {

    public static final double WEIGHT_SKILLS = 40.0;
    public static final double WEIGHT_LOCATION = 25.0;
    public static final double WEIGHT_EMPLOYMENT_TYPE = 15.0;
    public static final double WEIGHT_EXPERIENCE = 10.0;
    public static final double WEIGHT_SALARY = 10.0;

    /** One skill must outweigh this share of the skills weight to keep it readable. */
    private static final Pattern YEARS_PATTERN =
        Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*(?:\\+\\s*)?(?:years?|yrs?)\\b", Pattern.CASE_INSENSITIVE);

    /** Engines that treat "no experience" as a real requirement of zero years. */
    private static final Set<String> NO_EXPERIENCE_TOKENS = Set.of(
        "none", "no", "no experience", "no_experience", "noexperience",
        "entry level", "entry-level", "entry_level", "entrylevel",
        "fresh graduate", "fresh graduate welcome", "fresh graduates",
        "any", "0", "0 years");

    /**
     * Municipalities that share a land border inside the coverage area. Used for
     * the "nearby municipality" tier of the location score.
     */
    private static final Map<Municipality, List<Municipality>> NEARBY = new EnumMap<>(Municipality.class);

    static {
        NEARBY.put(Municipality.BASUD, List.of(Municipality.DAET));
        NEARBY.put(Municipality.DAET, List.of(Municipality.BASUD, Municipality.VINZONS, Municipality.LABO));
        NEARBY.put(Municipality.VINZONS, List.of(Municipality.DAET, Municipality.LABO));
        NEARBY.put(Municipality.LABO, List.of(Municipality.DAET, Municipality.VINZONS, Municipality.BASUD));
    }

    /** Employment types a seeker would realistically accept, not just their exact choice. */
    private static final Map<EmploymentType, List<EmploymentType>> COMPATIBLE_TYPES = new EnumMap<>(EmploymentType.class);

    static {
        COMPATIBLE_TYPES.put(EmploymentType.FULL_TIME, List.of(EmploymentType.CONTRACT));
        COMPATIBLE_TYPES.put(EmploymentType.PART_TIME, List.of(EmploymentType.CONTRACT, EmploymentType.TEMPORARY));
        COMPATIBLE_TYPES.put(EmploymentType.CONTRACT,
            List.of(EmploymentType.FULL_TIME, EmploymentType.PART_TIME, EmploymentType.TEMPORARY));
        COMPATIBLE_TYPES.put(EmploymentType.TEMPORARY, List.of(EmploymentType.CONTRACT, EmploymentType.PART_TIME));
    }

    /* ---------- public API ------------------------------------------------- */

    public MatchResultDto calculateMatch(JobSeekerProfile seeker, JobPost job) {
        List<MatchCriterionDto> criteria = new ArrayList<>();
        criteria.add(skillsCriterion(seeker, job));
        criteria.add(locationCriterion(seeker, job));
        criteria.add(employmentTypeCriterion(seeker, job));
        criteria.add(experienceCriterion(seeker, job));
        criteria.add(salaryCriterion(seeker, job));

        double applicable = 0;
        double earned = 0;
        boolean complete = true;
        for (MatchCriterionDto criterion : criteria) {
            if (criterion.isApplicable()) {
                applicable += criterion.getWeight();
                earned += criterion.getEarnedPoints();
            } else {
                complete = false;
            }
        }

        MatchResultDto result = new MatchResultDto();
        // Location is always judgeable for a real profile, so this cannot divide
        // by zero in practice; guard anyway and report a neutral score.
        double overall = applicable > 0 ? (earned / applicable) * 100.0 : 0.0;
        overall = clamp(overall);
        result.setOverallMatch(round1(overall));
        result.setClassification(classification(overall));
        result.setApplicableWeight(round1(applicable));
        result.setEarnedPoints(round1(earned));
        result.setCriteria(criteria);
        result.setComplete(complete);

        List<String> matched = new ArrayList<>();
        List<String> missing = new ArrayList<>();
        List<String> required = splitSkills(job.getRequiredSkills());
        Set<String> have = normalizedSkills(seeker.getSkills());
        for (String skill : required) {
            if (have.contains(normalizeSkill(skill))) {
                matched.add(skill);
            } else {
                missing.add(skill);
            }
        }
        result.setMatchedSkills(matched);
        result.setMissingSkills(missing);
        result.setTotalRequiredSkills(required.size());
        return result;
    }

    /** Maps a final percentage onto the bands shown in every interface. */
    public static String classification(double percentage) {
        if (percentage >= 90) return "Excellent Match";
        if (percentage >= 75) return "Strong Match";
        if (percentage >= 60) return "Good Match";
        if (percentage >= 40) return "Partial Match";
        return "Low Match";
    }

    /** Minimum percentage for a band label, or null when the band is unknown. */
    public static Integer minimumScoreForBand(String band) {
        if (band == null || band.isBlank()) return null;
        switch (band.trim().toLowerCase(Locale.ROOT)) {
            case "excellent": return 90;
            case "strong": return 75;
            case "good": return 60;
            case "partial": return 40;
            default: return null;
        }
    }

    /**
     * Matching-relevant profile completeness. It counts only the fields that
     * change a score, and lists exactly what is missing and where to add it.
     */
    public ProfileCompletenessDto profileCompleteness(JobSeekerProfile profile) {
        ProfileCompletenessDto dto = new ProfileCompletenessDto();
        if (profile == null) {
            return dto;
        }

        check(dto, "skills", "Add your skills", 25, "profile.html#skills",
            notBlank(profile.getSkills()));
        check(dto, "workExperience", "Add your work experience", 20, "profile.html#experience",
            notBlank(profile.getWorkExperience()));
        check(dto, "education", "Add your education", 15, "profile.html#education",
            profile.getEducationLevel() != null);
        check(dto, "location", "Set your preferred location", 20, "profile.html#location",
            profile.getMunicipality() != null && notBlank(profile.getBarangay()));
        check(dto, "employmentType", "Choose your preferred employment type", 10, "profile.html#preferences",
            profile.getEmploymentTypePreference() != null);
        check(dto, "salary", "Add your expected salary", 10, "profile.html#preferences",
            positive(profile.getExpectedSalaryMin()) || positive(profile.getExpectedSalaryMax()));

        int percent = 0;
        for (ProfileCompletenessDto.Item item : dto.getCompleted()) {
            percent += item.getWeight();
        }
        dto.setPercentage(percent);
        dto.setComplete(dto.getMissing().isEmpty());
        return dto;
    }

    /* ---------- criteria --------------------------------------------------- */

    private MatchCriterionDto skillsCriterion(JobSeekerProfile seeker, JobPost job) {
        List<String> required = splitSkills(job.getRequiredSkills());
        if (required.isEmpty()) {
            return MatchCriterionDto.unavailable("skills", "Skills", WEIGHT_SKILLS,
                "This employer did not list required skills, so skills are left out of the score.");
        }

        Set<String> have = normalizedSkills(seeker.getSkills());
        int matched = 0;
        for (String skill : required) {
            if (have.contains(normalizeSkill(skill))) {
                matched++;
            }
        }
        double score = (matched * 100.0) / required.size();
        double points = WEIGHT_SKILLS * score / 100.0;
        String status = matched == required.size() ? "MATCH" : matched == 0 ? "NONE" : "PARTIAL";
        String detail = matched + " of " + required.size() + " required skill"
            + (required.size() == 1 ? "" : "s") + " found in your profile";
        if (have.isEmpty()) {
            detail = "Your profile lists no skills yet — " + required.size()
                + " required skill" + (required.size() == 1 ? " is" : "s are") + " missing";
        }
        return new MatchCriterionDto("skills", "Skills", WEIGHT_SKILLS, true,
            round1(points), round1(score), status, detail);
    }

    private MatchCriterionDto locationCriterion(JobSeekerProfile seeker, JobPost job) {
        Municipality seekerMunicipality = seeker.getMunicipality();
        Municipality jobMunicipality = job.getMunicipality();

        if (seekerMunicipality == null || jobMunicipality == null) {
            return MatchCriterionDto.unavailable("location", "Location", WEIGHT_LOCATION,
                "One side has no municipality recorded, so location is left out of the score.");
        }

        String seekerBarangay = normalizePlace(seeker.getBarangay());
        String jobBarangay = normalizePlace(job.getBarangay());
        boolean sameBarangay = !seekerBarangay.isEmpty() && seekerBarangay.equals(jobBarangay);

        double score;
        String detail;
        if (jobMunicipality == seekerMunicipality && sameBarangay) {
            score = 100;
            detail = "Same barangay (" + job.getBarangay() + ", " + title(jobMunicipality) + ")";
        } else if (jobMunicipality == seekerMunicipality) {
            score = 80;
            detail = jobBarangay.isEmpty()
                ? "Same municipality (" + title(jobMunicipality) + ")"
                : "Same municipality (" + title(jobMunicipality) + "), different barangay";
        } else {
            List<Municipality> nearby = NEARBY.getOrDefault(seekerMunicipality, List.of());
            if (nearby.contains(jobMunicipality)) {
                score = 50;
                detail = "Nearby municipality: " + title(jobMunicipality)
                    + " borders your " + title(seekerMunicipality);
            } else {
                score = 10;
                detail = "Outside your preferred area (" + title(jobMunicipality)
                    + " is not " + title(seekerMunicipality) + " or next to it)";
            }
        }

        String status = score >= 100 ? "MATCH" : score >= 50 ? "PARTIAL" : "NONE";
        return new MatchCriterionDto("location", "Location", WEIGHT_LOCATION, true,
            round1(WEIGHT_LOCATION * score / 100.0), score, status, detail);
    }

    private MatchCriterionDto employmentTypeCriterion(JobSeekerProfile seeker, JobPost job) {
        EmploymentType preference = seeker.getEmploymentTypePreference();
        EmploymentType jobType = job.getEmploymentType();

        if (preference == null || jobType == null) {
            return MatchCriterionDto.unavailable("employmentType", "Employment type", WEIGHT_EMPLOYMENT_TYPE,
                preference == null
                    ? "You have not set a preferred employment type, so this is left out."
                    : "This employer did not state an employment type, so this is left out.");
        }

        double score;
        String detail;
        if (preference == jobType) {
            score = 100;
            detail = "Matches your preference (" + label(preference) + ")";
        } else if (COMPATIBLE_TYPES.getOrDefault(preference, List.of()).contains(jobType)) {
            score = 60;
            detail = label(jobType) + " is related to your " + label(preference) + " preference";
        } else {
            score = 0;
            detail = "You prefer " + label(preference) + "; this job is " + label(jobType);
        }

        String status = score >= 100 ? "MATCH" : score > 0 ? "PARTIAL" : "NONE";
        return new MatchCriterionDto("employmentType", "Employment type", WEIGHT_EMPLOYMENT_TYPE, true,
            round1(WEIGHT_EMPLOYMENT_TYPE * score / 100.0), score, status, detail);
    }

    private MatchCriterionDto experienceCriterion(JobSeekerProfile seeker, JobPost job) {
        ExperienceRequirement required = parseRequirement(job.getExperienceRequirement());
        if (required == null) {
            return MatchCriterionDto.unavailable("experience", "Experience", WEIGHT_EXPERIENCE,
                "This employer did not state an experience requirement, so it is left out.");
        }

        double years = seekerYears(seeker.getWorkExperience());
        double score;
        String detail;

        if (required.minYears <= 0) {
            score = 100;
            detail = "No experience required — suitable for fresh graduates";
        } else if (years <= 0) {
            score = 0;
            detail = "Requires " + yearsLabel(required) + "; your profile records no work experience";
        } else if (years >= required.minYears) {
            score = 100;
            detail = "You meet the " + yearsLabel(required) + " requirement";
        } else {
            score = clamp((years / required.minYears) * 100.0);
            detail = "You record about " + trimNumber(years) + " year" + (years == 1 ? "" : "s")
                + " of the " + yearsLabel(required) + " requested";
        }

        String status = score >= 100 ? "MATCH" : score > 0 ? "PARTIAL" : "NONE";
        return new MatchCriterionDto("experience", "Experience", WEIGHT_EXPERIENCE, true,
            round1(WEIGHT_EXPERIENCE * score / 100.0), round1(score), status, detail);
    }

    private MatchCriterionDto salaryCriterion(JobSeekerProfile seeker, JobPost job) {
        BigDecimal seekerMin = positive(seeker.getExpectedSalaryMin()) ? seeker.getExpectedSalaryMin() : null;
        BigDecimal seekerMax = positive(seeker.getExpectedSalaryMax()) ? seeker.getExpectedSalaryMax() : null;
        BigDecimal jobMin = positive(job.getSalaryMin()) ? job.getSalaryMin() : null;
        BigDecimal jobMax = positive(job.getSalaryMax()) ? job.getSalaryMax() : null;

        if (seekerMin == null && seekerMax == null) {
            return MatchCriterionDto.unavailable("salary", "Salary", WEIGHT_SALARY,
                "You have not set an expected salary, so salary is left out of the score.");
        }
        if (jobMin == null && jobMax == null) {
            return MatchCriterionDto.unavailable("salary", "Salary", WEIGHT_SALARY,
                "This employer did not disclose a salary, so salary is left out.");
        }

        double sMin = seekerMin == null ? 0 : seekerMin.doubleValue();
        Double sMax = seekerMax == null ? null : seekerMax.doubleValue();
        double jMin = jobMin == null ? 0 : jobMin.doubleValue();
        Double jMax = jobMax == null ? null : jobMax.doubleValue();

        double score;
        String detail;

        boolean reachesMinimum = jMax == null || jMax >= sMin;
        boolean startsBeforeMaximum = sMax == null || jMin <= sMax;

        if (reachesMinimum && startsBeforeMaximum) {
            if (jMin >= sMin) {
                score = 100;
                detail = "The offered range meets your expected minimum";
            } else if (jMax != null) {
                double span = jMax - jMin;
                double overlap = jMax - sMin;
                double ratio = span > 0 ? clamp((overlap / span) * 100.0) / 100.0 : 1.0;
                score = 50 + (50 * ratio);
                detail = "The range overlaps your expectation (about "
                    + Math.round(ratio * 100) + "% of the offered range is inside it)";
            } else {
                score = 60;
                detail = "The salary starts below your minimum but is open-ended";
            }
        } else {
            score = 0;
            detail = jMax != null && jMax < sMin
                ? "The offered maximum is below your expected minimum"
                : "The offered range is above your expected maximum";
        }

        String status = score >= 100 ? "MATCH" : score > 0 ? "PARTIAL" : "NONE";
        return new MatchCriterionDto("salary", "Salary", WEIGHT_SALARY, true,
            round1(WEIGHT_SALARY * score / 100.0), round1(score), status, detail);
    }

    /* ---------- parsing helpers ------------------------------------------- */

    /** Splits a comma/semicolon/newline separated list of skills, keeping the employer's wording. */
    public static List<String> splitSkills(String raw) {
        List<String> skills = new ArrayList<>();
        if (raw == null || raw.isBlank()) {
            return skills;
        }
        Set<String> seen = new HashSet<>();
        for (String part : raw.split("[,;\\n\\r•|]+")) {
            String cleaned = part.replaceAll("\\s+", " ").trim();
            if (cleaned.isEmpty()) {
                continue;
            }
            String key = normalizeSkill(cleaned);
            if (key.isEmpty() || !seen.add(key)) {
                continue;
            }
            skills.add(cleaned);
        }
        return skills;
    }

    /** Case, spacing and punctuation tolerant key. Unrelated words stay different. */
    public static String normalizeSkill(String raw) {
        if (raw == null) return "";
        return raw.toLowerCase(Locale.ROOT)
            .replaceAll("[\\u2018\\u2019]", "'")
            .replaceAll("\\s+", " ")
            .replaceAll("^[\\s.\\-]+|[\\s.\\-]+$", "")
            .trim();
    }

    private static Set<String> normalizedSkills(String raw) {
        Set<String> keys = new HashSet<>();
        for (String skill : splitSkills(raw)) {
            keys.add(normalizeSkill(skill));
        }
        return keys;
    }

    private static String normalizePlace(String raw) {
        return raw == null ? "" : raw.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ").trim();
    }

    private record ExperienceRequirement(double minYears, Double maxYears) {
    }

    private ExperienceRequirement parseRequirement(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String text = raw.toLowerCase(Locale.ROOT).replace("_", " ").trim();
        if (NO_EXPERIENCE_TOKENS.contains(text)
            || text.contains("no experience")
            || text.contains("without experience")
            || text.contains("entry level")
            || text.contains("fresh grad")) {
            return new ExperienceRequirement(0, null);
        }

        Matcher matcher = Pattern.compile("(\\d+(?:\\.\\d+)?)").matcher(text);
        if (!matcher.find()) {
            return null;
        }
        double first = Double.parseDouble(matcher.group(1));
        Double second = null;
        if (matcher.find()) {
            second = Double.parseDouble(matcher.group(1));
        }
        double min = first;
        Double max = second;
        // "2-3 years" is a minimum of two; "5+" or "at least 5" is five.
        if (max != null && max < min) {
            double swap = min;
            min = max;
            max = swap;
        }
        return new ExperienceRequirement(min, max);
    }

    /**
     * Years of experience recorded in free text. An explicit "N years" wins; a
     * non-empty work history without a number counts as one year, because the
     * seeker has recorded that they worked at least one job.
     */
    private double seekerYears(String workExperience) {
        if (workExperience == null || workExperience.isBlank()) {
            return 0;
        }
        Matcher matcher = YEARS_PATTERN.matcher(workExperience);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group(1));
            } catch (NumberFormatException ignored) {
                return 1;
            }
        }
        return 1;
    }

    private String yearsLabel(ExperienceRequirement requirement) {
        if (requirement.maxYears != null && requirement.maxYears > requirement.minYears) {
            return trimNumber(requirement.minYears) + "–" + trimNumber(requirement.maxYears) + " years";
        }
        return trimNumber(requirement.minYears) + " year" + (requirement.minYears == 1 ? "" : "s");
    }

    private static void check(ProfileCompletenessDto dto, String key, String label, int weight,
                              String href, boolean filled) {
        ProfileCompletenessDto.Item item = new ProfileCompletenessDto.Item(key, label, weight, href);
        if (filled) {
            dto.getCompleted().add(item);
        } else {
            dto.getMissing().add(item);
        }
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static boolean positive(BigDecimal value) {
        return value != null && value.compareTo(BigDecimal.ZERO) > 0;
    }

    private static String title(Municipality municipality) {
        String name = municipality.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String label(EmploymentType type) {
        String name = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static String trimNumber(double value) {
        return value == Math.rint(value) ? String.valueOf((long) value) : String.valueOf(value);
    }

    private static double clamp(double value) {
        return Math.max(0, Math.min(100, value));
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
