package com.jobskills;

import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.*;
import com.jobskills.service.MatchingEngine;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class MatchingEngineTest {

    private final MatchingEngine matchingEngine = new MatchingEngine();

    @Test
    void shouldCalculatePerfectMatch() {
        JobSeekerProfile seeker = createSeeker();
        JobPost job = createJob();

        var result = matchingEngine.calculateMatch(seeker, job);

        assertEquals(100.0, result.getOverallMatch());
        assertEquals(100.0, result.getSkillsMatch());
        assertEquals(100.0, result.getEducationMatch());
        assertEquals(100.0, result.getExperienceMatch());
        assertEquals(100.0, result.getCertificationsMatch());
        assertEquals(100.0, result.getLocationMatch());
        assertEquals(100.0, result.getAvailabilityMatch());
        assertEquals(100.0, result.getEmploymentTypeMatch());
        assertEquals(100.0, result.getSalaryMatch());
    }

    @Test
    void shouldCalculatePartialMatch() {
        JobSeekerProfile seeker = createSeeker();
        seeker.setSkills("Customer Service");
        seeker.setMunicipality(Municipality.BASUD);
        seeker.setEmploymentTypePreference(EmploymentType.FULL_TIME);

        JobPost job = createJob();

        var result = matchingEngine.calculateMatch(seeker, job);

        assertTrue(result.getOverallMatch() > 0);
        assertTrue(result.getOverallMatch() < 100);
        assertTrue(result.getLocationMatch() < 100);
        assertTrue(result.getEmploymentTypeMatch() < 100);
    }

    private JobSeekerProfile createSeeker() {
        return JobSeekerProfile.builder()
            .skills("Customer Service, Food Preparation, Cashier")
            .educationLevel(EducationLevel.HIGH_SCHOOL_GRADUATE)
            .workExperience("2 years")
            .certifications("Food Handler's Certificate")
            .municipality(Municipality.DAET)
            .availability(Availability.IMMEDIATELY)
            .employmentTypePreference(EmploymentType.PART_TIME)
            .expectedSalaryMin(BigDecimal.valueOf(12000))
            .expectedSalaryMax(BigDecimal.valueOf(15000))
            .build();
    }

    private JobPost createJob() {
        return JobPost.builder()
            .requiredSkills("Customer Service, Food Preparation, Cashier")
            .educationRequirement(EducationLevel.HIGH_SCHOOL_GRADUATE)
            .experienceRequirement("1-2 Years")
            .certifications("Food Handler's Certificate")
            .municipality(Municipality.DAET)
            .workSchedule(WorkSchedule.FLEXIBLE)
            .employmentType(EmploymentType.PART_TIME)
            .salaryMin(BigDecimal.valueOf(12000))
            .salaryMax(BigDecimal.valueOf(15000))
            .build();
    }
}