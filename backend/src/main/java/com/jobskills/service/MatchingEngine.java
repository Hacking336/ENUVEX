package com.jobskills.service;

import com.jobskills.dto.MatchResultDto;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;

@Service
public class MatchingEngine {

    private static final Map<String, Double> WEIGHTS = Map.ofEntries(
        Map.entry("skills", 0.25),
        Map.entry("education", 0.15),
        Map.entry("experience", 0.20),
        Map.entry("certifications", 0.10),
        Map.entry("location", 0.10),
        Map.entry("availability", 0.10),
        Map.entry("employmentType", 0.05),
        Map.entry("salary", 0.05)
    );

    private static final List<String> EDUCATION_HIERARCHY = List.of(
        "No Formal Education",
        "Elementary Graduate",
        "High School Graduate",
        "Vocational/Technical",
        "College Graduate",
        "Post Graduate"
    );

    private static final Map<String, List<String>> NEARBY_MUNICIPALITIES = Map.of(
        "BASUD", List.of("DAET"),
        "DAET", List.of("BASUD", "VINZONS"),
        "VINZONS", List.of("DAET", "LABO"),
        "LABO", List.of("VINZONS")
    );

    private static final Map<String, List<String>> AVAILABILITY_MAPPING = Map.of(
        "IMMEDIATELY", List.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "WEEKEND", "FLEXIBLE"),
        "WITHIN_1_WEEK", List.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "WEEKEND", "FLEXIBLE"),
        "WITHIN_2_WEEKS", List.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "WEEKEND", "FLEXIBLE"),
        "WITHIN_1_MONTH", List.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "WEEKEND", "FLEXIBLE"),
        "WITHIN_3_MONTHS", List.of("MORNING", "AFTERNOON", "EVENING", "NIGHT", "WEEKEND", "FLEXIBLE"),
        "MORE_THAN_3_MONTHS", List.of("FLEXIBLE")
    );

    private static final Map<String, List<String>> COMPATIBLE_EMPLOYMENT_TYPES = Map.of(
        "PART_TIME", List.of("CONTRACT", "TEMPORARY"),
        "FULL_TIME", List.of("CONTRACT"),
        "CONTRACT", List.of("PART_TIME", "FULL_TIME", "TEMPORARY"),
        "TEMPORARY", List.of("PART_TIME", "CONTRACT")
    );

    public MatchResultDto calculateMatch(JobSeekerProfile seeker, JobPost job) {
        double skillsMatch = calculateSkillsMatch(seeker.getSkills(), job.getRequiredSkills());
        double educationMatch = calculateEducationMatch(
            seeker.getEducationLevel() != null ? seeker.getEducationLevel().name().replace("_", " ") : "No Formal Education",
            job.getEducationRequirement() != null ? job.getEducationRequirement().name().replace("_", " ") : "No Formal Education"
        );
        double experienceMatch = calculateExperienceMatch(
            seeker.getWorkExperience(), job.getExperienceRequirement()
        );
        double certificationsMatch = calculateSkillsMatch(seeker.getCertifications(), job.getCertifications());
        double locationMatch = calculateLocationMatch(seeker.getMunicipality(), job.getMunicipality());
        double availabilityMatch = calculateAvailabilityMatch(seeker.getAvailability(), job.getWorkSchedule());
        double employmentTypeMatch = calculateEmploymentTypeMatch(seeker.getEmploymentTypePreference(), job.getEmploymentType());
        double salaryMatch = calculateSalaryMatch(
            seeker.getExpectedSalaryMin(), seeker.getExpectedSalaryMax(),
            job.getSalaryMin(), job.getSalaryMax()
        );

        double overallMatch = skillsMatch * WEIGHTS.get("skills") +
            educationMatch * WEIGHTS.get("education") +
            experienceMatch * WEIGHTS.get("experience") +
            certificationsMatch * WEIGHTS.get("certifications") +
            locationMatch * WEIGHTS.get("location") +
            availabilityMatch * WEIGHTS.get("availability") +
            employmentTypeMatch * WEIGHTS.get("employmentType") +
            salaryMatch * WEIGHTS.get("salary");

        MatchResultDto result = new MatchResultDto();
        result.setSkillsMatch(Math.round(skillsMatch * 100.0) / 100.0);
        result.setEducationMatch(Math.round(educationMatch * 100.0) / 100.0);
        result.setExperienceMatch(Math.round(experienceMatch * 100.0) / 100.0);
        result.setCertificationsMatch(Math.round(certificationsMatch * 100.0) / 100.0);
        result.setLocationMatch(Math.round(locationMatch * 100.0) / 100.0);
        result.setAvailabilityMatch(Math.round(availabilityMatch * 100.0) / 100.0);
        result.setEmploymentTypeMatch(Math.round(employmentTypeMatch * 100.0) / 100.0);
        result.setSalaryMatch(Math.round(salaryMatch * 100.0) / 100.0);
        result.setOverallMatch(Math.round(overallMatch * 100.0) / 100.0);

        return result;
    }

    private double calculateSkillsMatch(String seekerSkills, String jobSkills) {
        Set<String> seekerSet = normalizeText(seekerSkills);
        Set<String> jobSet = normalizeText(jobSkills);

        if (jobSet.isEmpty()) return 100.0;
        if (seekerSet.isEmpty()) return 0.0;

        Set<String> intersection = new HashSet<>(seekerSet);
        intersection.retainAll(jobSet);
        Set<String> union = new HashSet<>(seekerSet);
        union.addAll(jobSet);

        return (double) intersection.size() / union.size() * 100.0;
    }

    private Set<String> normalizeText(String text) {
        if (text == null || text.trim().isEmpty()) return Collections.emptySet();
        String[] words = text.toLowerCase().replace(",", " ").replace(";", " ").split("\\s+");
        Set<String> result = new HashSet<>();
        for (String word : words) {
            if (word.length() > 1) result.add(word.trim());
        }
        return result;
    }

    private double calculateEducationMatch(String seekerEdu, String jobEdu) {
        try {
            int seekerIdx = EDUCATION_HIERARCHY.indexOf(seekerEdu);
            int requiredIdx = EDUCATION_HIERARCHY.indexOf(jobEdu);

            if (requiredIdx == -1) return 100.0; // "Any" or unrecognized
            if (seekerIdx == -1) return 50.0;   // Unknown

            if (seekerIdx >= requiredIdx) return 100.0;

            int diff = requiredIdx - seekerIdx;
            return Math.max(0, 100 - (diff * 25));
        } catch (Exception e) {
            return seekerEdu.equalsIgnoreCase(jobEdu) ? 100.0 : 50.0;
        }
    }

    private double calculateExperienceMatch(String seekerExp, String jobExp) {
        if (jobExp == null || jobExp.equalsIgnoreCase("NO_EXPERIENCE") || jobExp.equalsIgnoreCase("ANY")) {
            return 100.0;
        }

        // Parse seeker experience from text (simplified)
        double seekerYears = parseExperienceYears(seekerExp);
        if (seekerYears < 0) return 50.0;

        // Parse job requirement
        try {
            if (jobExp.contains("-")) {
                String[] parts = jobExp.replace("Years", "").split("-");
                double min = Double.parseDouble(parts[0].trim());
                double max = Double.parseDouble(parts[1].trim());

                if (seekerYears < min) return Math.max(0, (seekerYears / min) * 100);
                if (seekerYears > max) return 100.0;
                return 100.0;
            } else if (jobExp.contains("+")) {
                double min = Double.parseDouble(jobExp.replace("+", "").replace("Years", "").trim());
                return seekerYears >= min ? 100.0 : (min > 0 ? (seekerYears / min) * 100 : 100.0);
            } else {
                double exact = Double.parseDouble(jobExp.replace("Years", "").trim());
                return seekerYears >= exact ? 100.0 : (exact > 0 ? (seekerYears / exact) * 100 : 100.0);
            }
        } catch (Exception e) {
            return 50.0;
        }
    }

    private double parseExperienceYears(String expText) {
        if (expText == null) return -1;
        try {
            // Extract first number from text
            String[] parts = expText.split("\\s+");
            for (String part : parts) {
                if (part.matches("\\d+")) return Double.parseDouble(part);
            }
        } catch (Exception e) {
            // ignore
        }
        return -1;
    }

    private double calculateLocationMatch(Municipality seekerMun, Municipality jobMun) {
        if (seekerMun == jobMun) return 100.0;
        if (seekerMun == null || jobMun == null) return 30.0;

        String seeker = seekerMun.name();
        String job = jobMun.name();

        List<String> nearby = NEARBY_MUNICIPALITIES.get(seeker);
        if (nearby != null && nearby.contains(job)) return 70.0;

        return 30.0;
    }

    private double calculateAvailabilityMatch(Availability seekerAvail, WorkSchedule jobSchedule) {
        if (seekerAvail == null || jobSchedule == null) return 50.0;

        List<String> valid = AVAILABILITY_MAPPING.get(seekerAvail.name());
        if (valid != null && valid.contains(jobSchedule.name())) return 100.0;
        if (jobSchedule == WorkSchedule.FLEXIBLE) return 80.0;
        return 40.0;
    }

    private double calculateEmploymentTypeMatch(EmploymentType seekerPref, EmploymentType jobType) {
        if (seekerPref == null || jobType == null) return 100.0;
        if (seekerPref == EmploymentType.CONTRACT || seekerPref == EmploymentType.TEMPORARY) {
            // Treat as "Any" for flexibility
            return 100.0;
        }
        if (seekerPref == jobType) return 100.0;

        List<String> compatible = COMPATIBLE_EMPLOYMENT_TYPES.get(seekerPref.name());
        if (compatible != null && compatible.contains(jobType.name())) return 70.0;

        return 40.0;
    }

    private double calculateSalaryMatch(BigDecimal seekerMin, BigDecimal seekerMax,
                                        BigDecimal jobMin, BigDecimal jobMax) {
        if ((seekerMin == null || seekerMin.compareTo(BigDecimal.ZERO) == 0) &&
            (seekerMax == null || seekerMax.compareTo(BigDecimal.ZERO) == 0)) {
            return 80.0;
        }
        if ((jobMin == null || jobMin.compareTo(BigDecimal.ZERO) == 0) &&
            (jobMax == null || jobMax.compareTo(BigDecimal.ZERO) == 0)) {
            return 80.0;
        }

        double sMin = seekerMin != null ? seekerMin.doubleValue() : 0;
        double sMax = seekerMax != null ? seekerMax.doubleValue() : Double.MAX_VALUE;
        double jMin = jobMin != null ? jobMin.doubleValue() : 0;
        double jMax = jobMax != null ? jobMax.doubleValue() : Double.MAX_VALUE;

        double overlapStart = Math.max(sMin, jMin);
        double overlapEnd = Math.min(sMax, jMax);

        if (overlapStart >= overlapEnd) return 0.0;

        double overlap = overlapEnd - overlapStart;
        double jobRange = jMax - jMin;
        if (Double.isInfinite(jobRange)) jobRange = sMax - sMin;
        if (Double.isInfinite(jobRange)) return 80.0;

        double match = (overlap / jobRange) * 100.0;
        return Math.min(100.0, match);
    }
}