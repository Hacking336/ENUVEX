package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * The full, explainable result of matching one job seeker against one job.
 *
 * The percentage is always {@code earnedPoints / applicableWeight * 100}, so a
 * criterion with missing data can neither inflate nor deflate it.
 */
@Data
public class MatchResultDto {

    /** Rounded percentage, 0-100. */
    private double overallMatch;

    /** Excellent Match, Strong Match, Good Match, Partial Match or Low Match. */
    private String classification;

    private double applicableWeight;
    private double earnedPoints;

    /** Required skills the seeker has, as the employer wrote them. */
    private List<String> matchedSkills = new ArrayList<>();

    /** Required skills the seeker does not have. */
    private List<String> missingSkills = new ArrayList<>();

    private int totalRequiredSkills;

    private List<MatchCriterionDto> criteria = new ArrayList<>();

    /** True when every criterion could be judged from real data. */
    private boolean complete;
}
