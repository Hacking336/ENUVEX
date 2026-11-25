package com.jobskills.controller;

import com.jobskills.config.CustomUserDetails;
import com.jobskills.dto.*;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.service.ApplicationService;
import com.jobskills.service.JobService;
import com.jobskills.service.MatchingEngine;
import com.jobskills.service.SkillsAnalyticsService;
import com.jobskills.service.SkillsGapService;
import com.jobskills.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/job-seekers")
@RequiredArgsConstructor
@PreAuthorize("hasRole('JOB_SEEKER')")
public class JobSeekerController {

    private final UserService userService;
    private final JobService jobService;
    private final ApplicationService applicationService;
    private final MatchingEngine matchingEngine;
    private final SkillsGapService skillsGapService;
    private final SkillsAnalyticsService skillsAnalyticsService;

    @GetMapping("/profile")
    public ResponseEntity<JobSeekerProfile> getProfile(Authentication authentication) {
        return ResponseEntity.ok(userService.getJobSeekerProfile(extractUserId(authentication)));
    }

    // No @Valid here: RegisterRequest's required email and password describe
    // registration only. UserService never writes them on this path and
    // validates every field it does write.
    @PutMapping("/profile")
    public ResponseEntity<JobSeekerProfile> updateProfile(
            Authentication authentication,
            @RequestPart("profile") RegisterRequest request,
            @RequestPart(value = "profilePhoto", required = false) MultipartFile photo) {
        return ResponseEntity.ok(userService.updateJobSeekerProfile(extractUserId(authentication), request, photo));
    }

    @GetMapping("/profile/completion")
    public ResponseEntity<Map<String, Integer>> getProfileCompletion(Authentication authentication) {
        JobSeekerProfile profile = userService.getJobSeekerProfile(extractUserId(authentication));
        return ResponseEntity.ok(Map.of("completion", userService.calculateProfileCompletion(profile)));
    }

    /** Profile fields the matching engine uses, with what is still missing. */
    @GetMapping("/profile/completeness")
    public ResponseEntity<ProfileCompletenessDto> getProfileCompleteness(Authentication authentication) {
        JobSeekerProfile profile = userService.getJobSeekerProfile(extractUserId(authentication));
        return ResponseEntity.ok(matchingEngine.profileCompleteness(profile));
    }

    @GetMapping("/jobs")
    public ResponseEntity<org.springframework.data.domain.Page<JobResponse>> searchJobs(
            JobSearchRequest search, Authentication authentication) {
        return ResponseEntity.ok(jobService.searchJobs(search, extractUserId(authentication)));
    }

    /** Map markers for the signed-in seeker, with match scores and distances. */
    @GetMapping("/jobs/map")
    public ResponseEntity<com.jobskills.dto.JobMapDto> mapJobs(
            JobSearchRequest search, Authentication authentication) {
        return ResponseEntity.ok(jobService.searchJobsMap(search, extractUserId(authentication)));
    }

    @GetMapping("/recommended")
    public ResponseEntity<org.springframework.data.domain.Page<JobResponse>> getRecommended(
            JobSearchRequest search, Authentication authentication) {
        return ResponseEntity.ok(jobService.recommendJobs(search, extractUserId(authentication)));
    }

    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobResponse> getJobDetails(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(jobService.getJobDetails(id, extractUserId(authentication)));
    }

    /** Score and per-criterion explanation for one job and the signed-in seeker. */
    @GetMapping("/jobs/{id}/match")
    public ResponseEntity<MatchResultDto> getJobMatch(@PathVariable Long id, Authentication authentication) {
        return ResponseEntity.ok(jobService.getMatchDetails(id, extractUserId(authentication)));
    }

    @PostMapping("/apply")
    public ResponseEntity<Map<String, Object>> apply(@RequestParam Long jobId, Authentication authentication) {
        var application = jobService.applyForJob(extractUserId(authentication), jobId);
        return ResponseEntity.status(201).body(Map.of(
            "id", application.getId(),
            "status", "APPLIED",
            "matchPercentage", application.getMatchPercentage()
        ));
    }

    @GetMapping("/applications")
    public ResponseEntity<List<ApplicationResponse>> getApplications(Authentication authentication) {
        return ResponseEntity.ok(applicationService.getSeekerApplications(extractUserId(authentication)));
    }

    /* ---------- skills gap analysis ---------------------------------- */

    @GetMapping("/skills-analysis")
    public ResponseEntity<SkillsAnalysisDto> skillsAnalysis(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) String barangay,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) JobCategory category,
            @RequestParam(required = false) Integer days,
            Authentication authentication) {
        return ResponseEntity.ok(skillsGapService.analyse(extractUserId(authentication),
            municipality, barangay, employmentType, category, days));
    }

    @GetMapping("/skills-demand")
    public ResponseEntity<SkillsAnalyticsDto> skillsDemand(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) String barangay,
            @RequestParam(required = false) EmploymentType employmentType,
            @RequestParam(required = false) JobCategory category,
            @RequestParam(required = false) Integer days) {
        return ResponseEntity.ok(skillsAnalyticsService.demand(municipality, barangay,
            employmentType, category, days));
    }

    @PostMapping("/skills-simulation")
    public ResponseEntity<SimulationResultDto> simulateSkills(
            @RequestBody SimulationRequest request, Authentication authentication) {
        return ResponseEntity.ok(skillsGapService.simulate(extractUserId(authentication), request));
    }

    @GetMapping("/career-opportunities")
    public ResponseEntity<CareerOpportunitiesDto> careerOpportunities(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) JobCategory category,
            Authentication authentication) {
        return ResponseEntity.ok(skillsGapService.careers(extractUserId(authentication),
            municipality, category));
    }

    @GetMapping("/learning-interests")
    public ResponseEntity<List<LearningInterestDto>> learningInterests(Authentication authentication) {
        return ResponseEntity.ok(skillsGapService.interests(extractUserId(authentication)));
    }

    @PostMapping("/learning-interests")
    public ResponseEntity<LearningInterestDto> addLearningInterest(
            @RequestParam String skill, Authentication authentication) {
        return ResponseEntity.status(201)
            .body(skillsGapService.addInterest(extractUserId(authentication), skill));
    }

    @DeleteMapping("/learning-interests")
    public ResponseEntity<Map<String, String>> removeLearningInterest(
            @RequestParam String skill, Authentication authentication) {
        skillsGapService.removeInterest(extractUserId(authentication), skill);
        return ResponseEntity.ok(Map.of("status", "removed"));
    }

    @PostMapping("/saved")
    public ResponseEntity<Map<String, String>> saveJob(@RequestParam Long jobId, Authentication authentication) {
        jobService.saveJob(extractUserId(authentication), jobId);
        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @DeleteMapping("/saved/{jobId}")
    public ResponseEntity<Map<String, String>> unsaveJob(@PathVariable Long jobId, Authentication authentication) {
        jobService.unsaveJob(extractUserId(authentication), jobId);
        return ResponseEntity.ok(Map.of("status", "unsaved"));
    }

    @GetMapping("/saved")
    public ResponseEntity<List<SavedJobResponse>> getSavedJobs(Authentication authentication) {
        return ResponseEntity.ok(jobService.getSavedJobsForSeeker(extractUserId(authentication)));
    }

    private Long extractUserId(Authentication authentication) {
        if (authentication == null || authentication.getPrincipal() == null) return null;
        Object principal = authentication.getPrincipal();
        if (principal instanceof CustomUserDetails details) {
            return details.getUserId();
        }
        return null;
    }
}
