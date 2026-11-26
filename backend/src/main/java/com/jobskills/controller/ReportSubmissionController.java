package com.jobskills.controller;

import com.jobskills.config.SecurityUtils;
import com.jobskills.dto.ReportDto;
import com.jobskills.model.Report;
import com.jobskills.model.User;
import com.jobskills.model.enums.ReportCategory;
import com.jobskills.repository.UserRepository;
import com.jobskills.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Report submission for signed-in job seekers and employers.
 *
 * <p>The reporter is resolved from the security context, so one account can
 * never file a report as another, and only the reporter's own reports are
 * returned. Nothing here can close, edit or action a report.
 */
@RestController
@RequestMapping("/api/moderation/reports")
@RequiredArgsConstructor
@PreAuthorize("isAuthenticated()")
public class ReportSubmissionController {

    private final ReportService reportService;
    private final UserRepository userRepository;

    /** Body of a new report; all fields are supplied by the reporter. */
    public record ReportRequest(String targetType, Long targetId,
                                ReportCategory category, String description) {
    }

    @PostMapping
    public ResponseEntity<ReportDto> submit(@RequestBody ReportRequest request) {
        User reporter = currentUser();
        Report report = reportService.submit(reporter, request.targetType(), request.targetId(),
            request.category(), request.description());
        return ResponseEntity.status(201).body(map(report));
    }

    @GetMapping("/mine")
    public ResponseEntity<List<ReportDto>> myReports() {
        return ResponseEntity.ok(reportService.myReports(currentUser()).stream()
            .map(this::map)
            .collect(Collectors.toList()));
    }

    private User currentUser() {
        Long id = SecurityUtils.getCurrentUserId();
        if (id == null) {
            throw new AccessDeniedException("You must be signed in.");
        }
        return userRepository.findById(id)
            .orElseThrow(() -> new AccessDeniedException("Account not found"));
    }

    private ReportDto map(Report report) {
        ReportDto dto = new ReportDto();
        dto.setId(report.getId());
        dto.setReportedTargetType(report.getReportedTargetType());
        dto.setReportedTargetId(report.getReportedTargetId());
        dto.setCategory(report.getCategory());
        dto.setDescription(report.getDescription());
        dto.setStatus(report.getStatus());
        dto.setCreatedAt(report.getCreatedAt());
        dto.setResolvedAt(report.getResolvedAt());
        dto.setIsDuplicate(report.isDuplicate());
        // The reporter's own email is the only identity returned, and only to
        // the reporter themselves.
        dto.setReporterEmail(report.getReporter() != null ? report.getReporter().getEmail() : null);
        return dto;
    }
}
