package com.jobskills.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * One saved job, with the job itself so the Saved Jobs screen does not have to
 * guess what was bookmarked. SavedJob hides both relations from JSON, so the
 * bookmark id alone is not enough for the client.
 */
@Data
public class SavedJobResponse {
    private Long id;
    private Long jobPostId;
    private LocalDateTime savedAt;
    private JobResponse job;
}
