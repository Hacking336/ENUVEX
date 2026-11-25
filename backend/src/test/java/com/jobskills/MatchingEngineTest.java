package com.jobskills;

import com.jobskills.dto.MatchCriterionDto;
import com.jobskills.dto.MatchResultDto;
import com.jobskills.dto.ProfileCompletenessDto;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.*;
import com.jobskills.service.MatchingEngine;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Manually calculated cases for the weighted matching engine.
 *
 * Base profile and base job differ only in the field under test, so every
 * expected number below can be checked by hand from the weights:
 * skills 40, location 25, employment type 15, experience 10, salary 10.
 */
class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    /* ---------- skills ----------------------------------------------------- */

    @Test
    @DisplayName("All required skills match")
    void allSkillsMatch() {
        MatchResultDto result = engine.calculateMatch(seeker(), job());

        assertEquals(40.0, criterion(result, "skills").getEarnedPoints(), 0.001);
        assertEquals(3, result.getMatchedSkills().size());
        assertEquals(0, result.getMissingSkills().size());
        assertEquals("MATCH", criterion(result, "skills").getStatus());
    }

    @Test
    @DisplayName("Some required skills match: 2 of 3 skills = 26.7 of 40 points")
    void someSkillsMatch() {
        JobSeekerProfile seeker = seeker();
        seeker.setSkills("Customer Service, Cashier");

        MatchResultDto result = engine.calculateMatch(seeker, job());

        assertEquals(2, result.getMatchedSkills().size());
        assertEquals(1, result.getMissingSkills().size());
        assertEquals("Food Preparation", result.getMissingSkills().get(0));
        assertEquals(26.7, criterion(result, "skills").getEarnedPoints(), 0.001);
        assertEquals(81.7, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("No required skills match")
    void noSkillsMatch() {
        JobSeekerProfile seeker = seeker();
        seeker.setSkills("Gardening, Welding");

        MatchResultDto result = engine.calculateMatch(seeker, job());

        assertEquals(0.0, criterion(result, "skills").getEarnedPoints(), 0.001);
        assertEquals("NONE", criterion(result, "skills").getStatus());
        assertEquals(55.0, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("Skill comparison ignores case and extra whitespace, never different words")
    void skillNormalization() {
        JobSeekerProfile seeker = seeker();
        seeker.setSkills("  customer   service , CASHIER , Food Preparation ");

        MatchResultDto result = engine.calculateMatch(seeker, job());
        assertEquals(3, result.getMatchedSkills().size());

        seeker.setSkills("Customer Services");
        MatchResultDto nearMiss = engine.calculateMatch(seeker, job());
        assertEquals(0, nearMiss.getMatchedSkills().size());
    }

    @Test
    @DisplayName("A job with no required skills leaves the criterion out, not at 100%")
    void jobWithoutRequiredSkills() {
        JobPost job = job();
        job.setRequiredSkills("   ");

        MatchResultDto result = engine.calculateMatch(seeker(), job);

        assertFalse(criterion(result, "skills").isApplicable());
        assertEquals("UNAVAILABLE", criterion(result, "skills").getStatus());
        // Location 20 + employment 15 + experience 10 + salary 10 = 55 of 60.
        assertEquals(60.0, result.getApplicableWeight(), 0.001);
        assertEquals(91.7, result.getOverallMatch(), 0.001);
        assertFalse(result.isComplete());
    }

    /* ---------- location --------------------------------------------------- */

    @Test
    @DisplayName("Same barangay is the strongest location match")
    void sameBarangay() {
        JobSeekerProfile seeker = seeker();
        seeker.setBarangay("Alawihao");
        JobPost job = job();
        job.setBarangay("Alawihao");

        MatchResultDto result = engine.calculateMatch(seeker, job);

        assertEquals(100.0, criterion(result, "location").getScore(), 0.001);
        assertEquals(25.0, criterion(result, "location").getEarnedPoints(), 0.001);
        assertEquals(100.0, result.getOverallMatch(), 0.001);
        assertEquals("Excellent Match", result.getClassification());
    }

    @Test
    @DisplayName("Same municipality, different barangay scores 80% of the location weight")
    void sameMunicipality() {
        JobSeekerProfile seeker = seeker();
        seeker.setBarangay("Alawihao");
        JobPost job = job();
        job.setBarangay("Borabod");

        MatchResultDto result = engine.calculateMatch(seeker, job);

        assertEquals(80.0, criterion(result, "location").getScore(), 0.001);
        assertEquals(20.0, criterion(result, "location").getEarnedPoints(), 0.001);
        assertEquals(95.0, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("A bordering municipality is a partial location match")
    void nearbyMunicipality() {
        JobSeekerProfile seeker = seeker();
        seeker.setMunicipality(Municipality.BASUD);
        JobPost job = job();
        job.setMunicipality(Municipality.DAET);

        MatchResultDto result = engine.calculateMatch(seeker, job);

        assertEquals(50.0, criterion(result, "location").getScore(), 0.001);
        assertEquals(12.5, criterion(result, "location").getEarnedPoints(), 0.001);
    }

    @Test
    @DisplayName("A municipality outside the preferred area scores low")
    void outsidePreferredArea() {
        JobSeekerProfile seeker = seeker();
        seeker.setMunicipality(Municipality.VINZONS);
        JobPost job = job();
        job.setMunicipality(Municipality.BASUD);

        MatchResultDto result = engine.calculateMatch(seeker, job);

        assertEquals(10.0, criterion(result, "location").getScore(), 0.001);
        assertEquals(2.5, criterion(result, "location").getEarnedPoints(), 0.001);
        assertEquals(77.5, result.getOverallMatch(), 0.001);
    }

    /* ---------- employment type ------------------------------------------- */

    @Test
    @DisplayName("Matching employment type earns the full 15 points")
    void employmentTypeMatch() {
        MatchResultDto result = engine.calculateMatch(seeker(), job());
        assertEquals(15.0, criterion(result, "employmentType").getEarnedPoints(), 0.001);
    }

    @Test
    @DisplayName("A related employment type earns partial credit")
    void employmentTypeRelated() {
        JobPost job = job();
        job.setEmploymentType(EmploymentType.CONTRACT); // related to PART_TIME

        MatchResultDto result = engine.calculateMatch(seeker(), job);
        assertEquals(60.0, criterion(result, "employmentType").getScore(), 0.001);
        assertEquals(9.0, criterion(result, "employmentType").getEarnedPoints(), 0.001);
    }

    @Test
    @DisplayName("No seeker preference is not a mismatch: the criterion is excluded")
    void noEmploymentPreference() {
        JobSeekerProfile seeker = seeker();
        seeker.setEmploymentTypePreference(null);

        MatchResultDto result = engine.calculateMatch(seeker, job());

        assertFalse(criterion(result, "employmentType").isApplicable());
        assertEquals(85.0, result.getApplicableWeight(), 0.001);
        // 40 skills + 20 location + 10 experience + 10 salary = 80 of 85.
        assertEquals(94.1, result.getOverallMatch(), 0.001);
    }

    /* ---------- experience ------------------------------------------------- */

    @Test
    @DisplayName("A job that requires no experience is suitable for a fresh graduate")
    void entryLevelJob() {
        JobSeekerProfile seeker = seeker();
        seeker.setWorkExperience(null);
        JobPost job = job();
        job.setExperienceRequirement("Entry level");

        MatchResultDto result = engine.calculateMatch(seeker, job);

        assertEquals(100.0, criterion(result, "experience").getScore(), 0.001);
        assertEquals(10.0, criterion(result, "experience").getEarnedPoints(), 0.001);
        assertEquals(95.0, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("A seeker without experience is scored against a real requirement")
    void noExperienceAgainstRequirement() {
        JobSeekerProfile seeker = seeker();
        seeker.setWorkExperience(null);
        JobPost job = job();
        job.setExperienceRequirement("2 years");

        MatchResultDto result = engine.calculateMatch(seeker, job);

        assertEquals(0.0, criterion(result, "experience").getScore(), 0.001);
        assertEquals(85.0, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("Partial experience earns proportional credit")
    void partialExperience() {
        JobSeekerProfile seeker = seeker();
        seeker.setWorkExperience("1 year");
        JobPost job = job();
        job.setExperienceRequirement("2 years");

        MatchResultDto result = engine.calculateMatch(seeker, job);
        assertEquals(50.0, criterion(result, "experience").getScore(), 0.001);
        assertEquals(5.0, criterion(result, "experience").getEarnedPoints(), 0.001);
    }

    @Test
    @DisplayName("An unstated experience requirement is excluded")
    void experienceNotStated() {
        JobPost job = job();
        job.setExperienceRequirement(null);

        MatchResultDto result = engine.calculateMatch(seeker(), job);
        assertFalse(criterion(result, "experience").isApplicable());
        assertEquals(90.0, result.getApplicableWeight(), 0.001);
    }

    /* ---------- salary ----------------------------------------------------- */

    @Test
    @DisplayName("A job with no disclosed salary excludes salary instead of inventing one")
    void jobWithoutSalary() {
        JobPost job = job();
        job.setSalaryMin(null);
        job.setSalaryMax(null);

        MatchResultDto result = engine.calculateMatch(seeker(), job);

        assertFalse(criterion(result, "salary").isApplicable());
        assertEquals(90.0, result.getApplicableWeight(), 0.001);
        // 40 skills + 20 location + 15 type + 10 experience = 85 of 90.
        assertEquals(94.4, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("A seeker with no salary preference excludes salary")
    void seekerWithoutSalary() {
        JobSeekerProfile seeker = seeker();
        seeker.setExpectedSalaryMin(null);
        seeker.setExpectedSalaryMax(null);

        MatchResultDto result = engine.calculateMatch(seeker, job());

        assertFalse(criterion(result, "salary").isApplicable());
        assertEquals(94.4, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("Overlapping salary ranges earn partial credit")
    void overlappingSalary() {
        JobPost job = job();
        job.setSalaryMin(BigDecimal.valueOf(10000));
        job.setSalaryMax(BigDecimal.valueOf(13000));

        MatchResultDto result = engine.calculateMatch(seeker(), job);

        // Overlap 12,000-13,000 is one third of the 10,000-13,000 offer: 50 + 16.7.
        assertEquals(66.7, criterion(result, "salary").getScore(), 0.001);
        assertEquals(6.7, criterion(result, "salary").getEarnedPoints(), 0.001);
    }

    @Test
    @DisplayName("A salary below the preferred minimum scores zero")
    void salaryBelowMinimum() {
        JobPost job = job();
        job.setSalaryMin(BigDecimal.valueOf(8000));
        job.setSalaryMax(BigDecimal.valueOf(10000));

        MatchResultDto result = engine.calculateMatch(seeker(), job);
        assertEquals(0.0, criterion(result, "salary").getScore(), 0.001);
        assertEquals(85.0, result.getOverallMatch(), 0.001);
    }

    /* ---------- normalization --------------------------------------------- */

    @Test
    @DisplayName("An incomplete job is scored on the weights that can be judged")
    void incompleteJobRequirements() {
        JobPost job = JobPost.builder()
            .requiredSkills("Customer Service")
            .municipality(Municipality.DAET)
            .build();

        MatchResultDto result = engine.calculateMatch(seeker(), job);

        // Only skills (40) and location (25) can be judged: 40 + 20 = 60 of 65.
        assertEquals(65.0, result.getApplicableWeight(), 0.001);
        assertEquals(60.0, result.getEarnedPoints(), 0.001);
        assertEquals(92.3, result.getOverallMatch(), 0.001);
        assertFalse(result.isComplete());
    }

    @Test
    @DisplayName("Final percentage is always earned points divided by applicable weight")
    void percentageUsesOnlyApplicableWeight() {
        JobSeekerProfile seeker = seeker();
        seeker.setSkills("Customer Service");
        JobPost job = job();
        job.setSalaryMin(null);
        job.setSalaryMax(null);

        MatchResultDto result = engine.calculateMatch(seeker, job);
        double applicable = result.getCriteria().stream()
            .filter(MatchCriterionDto::isApplicable)
            .mapToDouble(MatchCriterionDto::getWeight).sum();
        double earned = result.getCriteria().stream()
            .filter(MatchCriterionDto::isApplicable)
            .mapToDouble(MatchCriterionDto::getEarnedPoints).sum();

        assertEquals(90.0, applicable, 0.001);
        assertEquals(Math.round((earned / applicable) * 1000.0) / 10.0, result.getOverallMatch(), 0.001);
    }

    @Test
    @DisplayName("Classification bands follow the documented thresholds")
    void classificationBands() {
        assertEquals("Excellent Match", MatchingEngine.classification(90));
        assertEquals("Strong Match", MatchingEngine.classification(89.9));
        assertEquals("Strong Match", MatchingEngine.classification(75));
        assertEquals("Good Match", MatchingEngine.classification(74.9));
        assertEquals("Good Match", MatchingEngine.classification(60));
        assertEquals("Partial Match", MatchingEngine.classification(59.9));
        assertEquals("Partial Match", MatchingEngine.classification(40));
        assertEquals("Low Match", MatchingEngine.classification(39.9));
    }

    /* ---------- profile completeness --------------------------------------- */

    @Test
    @DisplayName("Profile completeness lists exactly what improves matching")
    void profileCompleteness() {
        JobSeekerProfile seeker = seeker();
        seeker.setBarangay(null);
        seeker.setEducationLevel(null);

        ProfileCompletenessDto completeness = engine.profileCompleteness(seeker);

        // Skills 25 + experience 20 + employment type 10 + salary 10 = 65.
        assertEquals(65, completeness.getPercentage());
        assertFalse(completeness.isComplete());
        List<String> missing = completeness.getMissing().stream()
            .map(ProfileCompletenessDto.Item::getKey).collect(Collectors.toList());
        assertEquals(List.of("education", "location"), missing);
    }

    @Test
    @DisplayName("A fully matching-relevant profile reports 100%")
    void completeProfile() {
        JobSeekerProfile seeker = seeker();
        seeker.setBarangay("Alawihao");

        ProfileCompletenessDto completeness = engine.profileCompleteness(seeker);
        assertEquals(100, completeness.getPercentage());
        assertTrue(completeness.isComplete());
    }

    /* ---------- fixtures --------------------------------------------------- */

    private MatchCriterionDto criterion(MatchResultDto result, String key) {
        Map<String, MatchCriterionDto> byKey = result.getCriteria().stream()
            .collect(Collectors.toMap(MatchCriterionDto::getKey, Function.identity()));
        assertTrue(byKey.containsKey(key), "Missing criterion " + key);
        return byKey.get(key);
    }

    private JobSeekerProfile seeker() {
        return JobSeekerProfile.builder()
            .skills("Customer Service, Food Preparation, Cashier")
            .educationLevel(EducationLevel.HIGH_SCHOOL_GRADUATE)
            .workExperience("2 years")
            .municipality(Municipality.DAET)
            .employmentTypePreference(EmploymentType.PART_TIME)
            .expectedSalaryMin(BigDecimal.valueOf(12000))
            .expectedSalaryMax(BigDecimal.valueOf(15000))
            .build();
    }

    private JobPost job() {
        return JobPost.builder()
            .jobTitle("Service Crew")
            .requiredSkills("Customer Service, Food Preparation, Cashier")
            .educationRequirement(EducationLevel.HIGH_SCHOOL_GRADUATE)
            .experienceRequirement("1-2 Years")
            .municipality(Municipality.DAET)
            .employmentType(EmploymentType.PART_TIME)
            .salaryMin(BigDecimal.valueOf(12000))
            .salaryMax(BigDecimal.valueOf(15000))
            .build();
    }
}
