package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Result of the opportunity simulator. All potential values are simulations. */
@Data
public class SimulationResultDto {
    private int threshold;
    private List<String> addedSkills = new ArrayList<>();

    /** Active jobs at or above the threshold with the real profile. */
    private int currentMatchingJobs;

    /** Active jobs at or above the threshold in the simulation. */
    private int potentialMatchingJobs;
    private int additionalMatchingJobs;

    /** Averages over active jobs that actually list required skills. */
    private double currentAverageScore;
    private double potentialAverageScore;
    private double averageScoreGain;

    private int jobsEvaluated;

    private List<SimulationJobDto> newlyUnlocked = new ArrayList<>();
    private List<SimulationJobDto> improved = new ArrayList<>();
    private List<SimulationJobDto> improvedButBelowThreshold = new ArrayList<>();

    /** Skills still missing across the newly unlocked jobs, excluding the simulated ones. */
    private List<Map<String, Object>> remainingGaps = new ArrayList<>();

    private List<SimulationJobDto> jobs = new ArrayList<>();

    /** Plain-language reminder that this is a simulation and not a guarantee. */
    private String explanation;

    /** Always true, so the interface can label every potential score. */
    private boolean simulation = true;
}
