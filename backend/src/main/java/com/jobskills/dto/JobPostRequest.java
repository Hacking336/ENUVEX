package com.jobskills.dto;

import com.jobskills.model.enums.EducationLevel;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.model.enums.WorkSchedule;
import lombok.Data;

import java.math.BigDecimal;

/**
 * What a client may send when creating or updating a job post.
 *
 * A DTO is used instead of binding straight onto the JobPost entity because the
 * entity's defaults (active = true, availablePositions = 1) are only applied by
 * Lombok's builder: a body deserialized into a new JobPost leaves both at their
 * Java zero values, which published every new post as an inactive 0-position
 * job and re-deactivated a post on every edit that omitted the field.
 *
 * Every field is nullable so an update can change one thing and leave the rest
 * alone; JobService decides which values are required and which default.
 */
@Data
public class JobPostRequest {

    private String jobTitle;
    private JobCategory jobCategory;
    private String jobDescription;
    private String responsibilities;
    private String requiredSkills;
    private EducationLevel educationRequirement;
    private String experienceRequirement;
    private String certifications;
    private BigDecimal salaryMin;
    private BigDecimal salaryMax;
    private EmploymentType employmentType;
    private WorkSchedule workSchedule;
    private Integer availablePositions;
    private Municipality municipality;
    private String barangay;
    private String jobLocation;

    /** null on create means "publish"; null on update means "leave unchanged". */
    private Boolean active;
}
