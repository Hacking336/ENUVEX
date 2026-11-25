package com.jobskills.controller;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jobskills.dto.GeoBarangayDto;
import com.jobskills.dto.GeoCoverageDto;
import com.jobskills.dto.GeoMapConfigDto;
import com.jobskills.dto.GeoMunicipalityDto;
import com.jobskills.model.JobPost;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.service.GeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Geographic reference endpoints used by every map screen.
 *
 * All of it is read-only and non-personal: municipality and barangay reference
 * data, boundary GeoJSON, and the tile source configuration. No seeker location
 * is ever accepted or stored here.
 */
@RestController
@RequestMapping("/api/geo")
@RequiredArgsConstructor
public class GeoController {

    private final GeoService geoService;
    private final JobPostRepository jobPostRepository;

    @GetMapping("/coverage")
    public ResponseEntity<GeoCoverageDto> coverage() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
            .body(geoService.coverage());
    }

    @GetMapping("/map-config")
    public ResponseEntity<GeoMapConfigDto> mapConfig() {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
            .body(geoService.mapConfig());
    }

    @GetMapping("/municipalities")
    public ResponseEntity<List<GeoMunicipalityDto>> municipalities() {
        return ResponseEntity.ok(geoService.coverage().municipalities());
    }

    @GetMapping("/municipalities/{code}/barangays")
    public ResponseEntity<List<GeoBarangayDto>> barangays(@PathVariable String code) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofHours(6)).cachePublic())
            .body(geoService.barangays(code));
    }

    /**
     * Active job-post counts per municipality and barangay, keyed by PSGC code.
     * Only counts are returned: no employer address and no person's location.
     */
    @GetMapping("/job-counts")
    public ResponseEntity<Map<String, Object>> jobCounts() {
        Map<String, Long> byMunicipality = new LinkedHashMap<>();
        Map<String, Long> byBarangay = new LinkedHashMap<>();
        long total = 0;

        for (JobPost job : jobPostRepository.findByIsActiveTrue()) {
            total += 1;
            String municipalityCode = job.getMunicipalityCode();
            if ((municipalityCode == null || municipalityCode.isBlank()) && job.getMunicipality() != null) {
                municipalityCode = geoService.municipalityCode(job.getMunicipality());
            }
            if (municipalityCode != null) {
                byMunicipality.merge(municipalityCode, 1L, Long::sum);
            }
            if (job.getBarangayCode() != null && !job.getBarangayCode().isBlank()) {
                byBarangay.merge(job.getBarangayCode(), 1L, Long::sum);
            }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("municipalities", byMunicipality);
        result.put("barangays", byBarangay);
        result.put("totalActiveJobs", total);
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofMinutes(2)).cachePublic())
            .body(result);
    }

    /**
     * Boundary layer as GeoJSON. The frontend caches each layer per session, so
     * a boundary file is downloaded once instead of on every filter change.
     */
    @GetMapping("/boundaries")
    public ResponseEntity<ObjectNode> boundaries(
            @RequestParam(defaultValue = "municipality") String level,
            @RequestParam(required = false) String municipality) {
        return ResponseEntity.ok()
            .cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePublic())
            .body(geoService.boundaries(level, municipality));
    }
}
