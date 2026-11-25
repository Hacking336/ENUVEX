package com.jobskills.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * One weighted criterion of a match, as the seeker or employer sees it.
 *
 * {@code applicable} is false when the data needed to judge the criterion is
 * missing on either side. Such a criterion is left out of the final percentage
 * instead of counting as a mismatch or a perfect score.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MatchCriterionDto {

    /** Stable key: skills, location, employmentType, experience, salary. */
    private String key;

    private String label;

    /** The weight this criterion carries when it can be judged. */
    private double weight;

    private boolean applicable;

    /** Points earned out of {@code weight}; 0 when not applicable. */
    private double earnedPoints;

    /** Percentage inside this criterion, 0-100. Null when not applicable. */
    private Double score;

    /** MATCH, PARTIAL, NONE or UNAVAILABLE. */
    private String status;

    /** Plain-language explanation built from the real data. */
    private String detail;

    public static MatchCriterionDto unavailable(String key, String label, double weight, String detail) {
        return new MatchCriterionDto(key, label, weight, false, 0d, null, "UNAVAILABLE", detail);
    }
}
