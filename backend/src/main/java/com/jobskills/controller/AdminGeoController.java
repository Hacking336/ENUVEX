package com.jobskills.controller;

import com.jobskills.model.JobApplication;
import com.jobskills.model.JobPost;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.*;
import com.jobskills.service.GeoService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Geographic analytics for the admin console.
 *
 * Every number is counted from the database under the requested filters. No
 * seeker coordinate, address or other personal location is read or returned -
 * only the municipality already stored on a profile is aggregated, which is the
 * same field the admin reports already use.
 */
@RestController
@RequestMapping("/api/admin/geo")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminGeoController {

    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final GeoService geoService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats(
            @RequestParam(required = false) Municipality municipality,
            @RequestParam(required = false) String barangay,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDateTime fromTime = from == null ? null : from.atStartOfDay();
        LocalDateTime toTime = to == null ? null : to.plusDays(1).atStartOfDay().minusNanos(1);
        String barangayFilter = barangay == null || barangay.isBlank() ? null : barangay.trim();

        // Link each municipality enum to its PSGC code so the map and the table
        // use exactly the same key as the boundary layer.
        Map<Municipality, String> codeByMunicipality = new EnumMap<>(Municipality.class);
        Map<String, Municipality> municipalityByCode = new HashMap<>();
        for (Municipality value : Municipality.values()) {
            String code = geoService.municipalityCode(value);
            if (code != null) {
                codeByMunicipality.put(value, code);
                municipalityByCode.put(code, value);
            }
        }

        Map<Municipality, long[]> jobs = new EnumMap<>(Municipality.class);
        Map<Municipality, long[]> pins = new EnumMap<>(Municipality.class);
        Map<Municipality, Long> applications = new EnumMap<>(Municipality.class);
        Map<String, long[]> jobsByBarangay = new LinkedHashMap<>();

        for (JobPost job : jobPostRepository.findAll()) {
            if (!inRange(job.getCreatedAt(), fromTime, toTime)) {
                continue;
            }
            if (municipality != null && job.getMunicipality() != municipality) {
                continue;
            }
            if (barangayFilter != null) {
                boolean matchesCode = barangayFilter.equals(job.getBarangayCode());
                boolean matchesName = job.getBarangay() != null && job.getBarangay().equalsIgnoreCase(barangayFilter);
                if (!matchesCode && !matchesName) {
                    continue;
                }
            }
            long[] counts = jobs.computeIfAbsent(job.getMunicipality(), key -> new long[2]);
            counts[0] += 1;
            if (job.isActive()) {
                counts[1] += 1;
            }
            if (job.getLatitude() != null && job.getLongitude() != null) {
                long[] pinned = pins.computeIfAbsent(job.getMunicipality(), key -> new long[1]);
                pinned[0] += 1;
            }
            if (job.getBarangayCode() != null) {
                long[] barangayCounts = jobsByBarangay.computeIfAbsent(job.getBarangayCode(), key -> new long[2]);
                barangayCounts[0] += 1;
                if (job.isActive()) {
                    barangayCounts[1] += 1;
                }
            }
        }

        for (JobApplication application : applicationRepository.findAll()) {
            if (!inRange(application.getAppliedAt(), fromTime, toTime)) {
                continue;
            }
            JobPost job = application.getJobPost();
            if (job == null) {
                continue;
            }
            if (municipality != null && job.getMunicipality() != municipality) {
                continue;
            }
            if (barangayFilter != null) {
                boolean matchesCode = barangayFilter.equals(job.getBarangayCode());
                boolean matchesName = job.getBarangay() != null && job.getBarangay().equalsIgnoreCase(barangayFilter);
                if (!matchesCode && !matchesName) {
                    continue;
                }
            }
            applications.merge(job.getMunicipality(), 1L, Long::sum);
        }

        Map<Municipality, Long> employers = new EnumMap<>(Municipality.class);
        employerRepository.findAll().forEach(employer -> {
            if (!inRange(employer.getCreatedAt(), fromTime, toTime)) {
                return;
            }
            if (municipality != null && employer.getMunicipality() != municipality) {
                return;
            }
            if (barangayFilter != null && (employer.getBarangay() == null
                || !employer.getBarangay().equalsIgnoreCase(barangayFilter))) {
                return;
            }
            employers.merge(employer.getMunicipality(), 1L, Long::sum);
        });

        Map<Municipality, Long> seekers = new EnumMap<>(Municipality.class);
        seekerRepository.findAll().forEach(seeker -> {
            if (!inRange(seeker.getCreatedAt(), fromTime, toTime)) {
                return;
            }
            if (municipality != null && seeker.getMunicipality() != municipality) {
                return;
            }
            if (barangayFilter != null && (seeker.getBarangay() == null
                || !seeker.getBarangay().equalsIgnoreCase(barangayFilter))) {
                return;
            }
            seekers.merge(seeker.getMunicipality(), 1L, Long::sum);
        });

        List<Map<String, Object>> municipalityRows = new ArrayList<>();
        long totalJobs = 0, totalActiveJobs = 0, totalPins = 0;
        long totalEmployers = 0, totalSeekers = 0, totalApplications = 0;

        for (com.jobskills.dto.GeoMunicipalityDto geo : geoService.coverage().municipalities()) {
            Municipality value = geo.value();
            long[] jobCounts = jobs.getOrDefault(value, new long[2]);
            long employerCount = employers.getOrDefault(value, 0L);
            long seekerCount = seekers.getOrDefault(value, 0L);
            long applicationCount = applications.getOrDefault(value, 0L);
            long pinCount = pins.getOrDefault(value, new long[1])[0];

            totalJobs += jobCounts[0];
            totalActiveJobs += jobCounts[1];
            totalPins += pinCount;
            totalEmployers += employerCount;
            totalSeekers += seekerCount;
            totalApplications += applicationCount;

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", geo.code());
            row.put("name", geo.name());
            row.put("municipality", value.name());
            row.put("jobs", jobCounts[0]);
            row.put("activeJobs", jobCounts[1]);
            row.put("jobsWithPin", pinCount);
            row.put("employers", employerCount);
            row.put("jobSeekers", seekerCount);
            row.put("applications", applicationCount);
            municipalityRows.add(row);
        }

        List<Map<String, Object>> barangayRows = new ArrayList<>();
        for (Map.Entry<String, long[]> entry : jobsByBarangay.entrySet()) {
            String code = entry.getKey();
            String name = geoService.barangayName(code);
            String municipalityCode = code.substring(0, Math.max(0, code.length() - 3)) + "000";
            Municipality value = municipalityByCode.get(municipalityCode);
            if (name == null) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("code", code);
            row.put("name", name);
            row.put("municipalityCode", municipalityCode);
            row.put("municipality", value == null ? null : value.name());
            row.put("jobs", entry.getValue()[0]);
            row.put("activeJobs", entry.getValue()[1]);
            barangayRows.add(row);
        }
        barangayRows.sort(Comparator.comparingLong(row -> -((Number) row.get("jobs")).longValue()));

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("jobs", totalJobs);
        totals.put("activeJobs", totalActiveJobs);
        totals.put("jobsWithPin", totalPins);
        totals.put("jobsWithoutPin", totalJobs - totalPins);
        totals.put("employers", totalEmployers);
        totals.put("jobSeekers", totalSeekers);
        totals.put("applications", totalApplications);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("municipality", municipality == null ? null : municipality.name());
        filters.put("barangay", barangayFilter);
        filters.put("from", from == null ? null : from.toString());
        filters.put("to", to == null ? null : to.toString());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("municipalities", municipalityRows);
        result.put("barangays", barangayRows);
        result.put("totals", totals);
        result.put("filters", filters);
        result.put("coverageAreas", geoService.coverage().municipalities().stream()
            .map(com.jobskills.dto.GeoMunicipalityDto::value)
            .map(Enum::name)
            .toList());
        result.put("generatedAt", LocalDateTime.now().toString());
        return ResponseEntity.ok(result);
    }

    private boolean inRange(LocalDateTime value, LocalDateTime from, LocalDateTime to) {
        if (value == null) {
            return from == null && to == null;
        }
        if (from != null && value.isBefore(from)) {
            return false;
        }
        return to == null || !value.isAfter(to);
    }
}
