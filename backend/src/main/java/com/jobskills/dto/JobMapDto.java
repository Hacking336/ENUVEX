package com.jobskills.dto;

import java.util.List;

/**
 * Map payload for the current search filters.
 *
 * Only jobs with confirmed coordinates are in {@code markers}. Every other
 * matching job is still in the list results, and {@code jobsWithoutCoordinates}
 * says how many were left off the map so the page can state it honestly.
 */
public record JobMapDto(
    List<JobResponse> markers,
    long totalElements,
    long jobsWithoutCoordinates,
    boolean truncated
) {
}
