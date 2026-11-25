package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * One job evaluated against the real profile and against a hypothetical profile
 * with the selected skills added. The official score is never changed; every
 * simulated value is labelled as a simulation.
 */
@Data
public class SimulationJobDto {
    private Long id;
    private String jobTitle;
    private String companyName;
    private String municipality;
    private String barangay;
    private String employmentType;
    private String category;

    /** Official MatchingEngine score for the current profile. */
    private double currentScore;
    private String currentClassification;

    /** Simulation only: score with the selected skills added to a copy of the profile. */
    private double potentialScore;
    private String potentialClassification;

    private double delta;

    /** False -> true across the selected threshold. */
    private boolean newlyQualified;

    /** True when the score rises but the job still sits below the threshold. */
    private boolean improvedButBelowThreshold;

    /** Required skills still missing after the simulation. */
    private List<String> remainingMissingSkills = new ArrayList<>();

    /** True when at least one requirement besides skills is unmet or unjudgeable. */
    private boolean otherRequirementsIncomplete;
}
