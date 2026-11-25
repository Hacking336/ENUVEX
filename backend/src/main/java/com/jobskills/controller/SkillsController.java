package com.jobskills.controller;

import com.jobskills.dto.SkillCatalogDto;
import com.jobskills.model.LearningResource;
import com.jobskills.service.SkillsGapService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Shared, read-only skill data for signed-in users. Employers use the catalog
 * to pick consistent skills when posting a job, and seekers use it to add
 * skills and to look up admin-verified learning resources.
 */
@RestController
@RequestMapping("/api/skills")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class SkillsController {

    private final SkillsGapService skillsGapService;

    @GetMapping("/catalog")
    public ResponseEntity<List<SkillCatalogDto>> catalog(
            @RequestParam(required = false) String q,
            @RequestParam(required = false, defaultValue = "20") Integer limit) {
        return ResponseEntity.ok(skillsGapService.catalog(q, limit));
    }

    @GetMapping("/resources")
    public ResponseEntity<List<LearningResource>> resources(@RequestParam String skill) {
        return ResponseEntity.ok(skillsGapService.resourcesForSkill(skill));
    }
}
