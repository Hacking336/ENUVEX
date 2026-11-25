package com.jobskills.dto;

import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.model.enums.WorkSchedule;
import lombok.Data;

@Data
public class JobSearchRequest {
    private Municipality municipality;
    private String barangay;
    private JobCategory category;
    private String skills;
    private Double minSalary;
    private Double maxSalary;
    private EmploymentType employmentType;
    private WorkSchedule workSchedule;
    private Double maxDistance;
    /** Near Me: the seeker's temporary position, used only for this request. */
    private Double nearLatitude;
    private Double nearLongitude;
    private Double radiusKm;
    private String experience;
    private String education;
    /** Recommended-job band: excellent, strong, good or partial. Null means all. */
    private String matchBand;
    /** Explicit cut-off (0-100); overrides matchBand when both are sent. */
    private Integer minMatchScore;
    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String sortDir = "DESC";
}