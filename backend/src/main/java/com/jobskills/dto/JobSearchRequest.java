package com.jobskills.dto;

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
    private String employmentType;
    private WorkSchedule workSchedule;
    private Double maxDistance;
    private String experience;
    private String education;
    private int page = 0;
    private int size = 20;
    private String sortBy = "createdAt";
    private String sortDir = "DESC";
}