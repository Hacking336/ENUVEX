package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * A missing skill with its transparent priority calculation. Every number is
 * derived from stored job postings and the seeker's own profile.
 */
@Data
public class SkillPriorityDto {
    private String skill;
    private String skillKey;

    /** Active postings in the selected area requiring the skill. */
    private int areaJobCount;

    /** Active postings anywhere requiring the skill, for context. */
    private int totalJobCount;

    /** Postings where adding this skill alone reaches the match threshold. */
    private int opportunityJobs;

    /** Postings where adding this skill raises the official score at all. */
    private int improvedJobs;

    /** Postings where the skill improves the score but the threshold is still unmet. */
    private int improvedButBelowThreshold;

    /** areaJobCount / area postings that list any skill * 100. Null when none list skills. */
    private Double sharePercent;

    /** Share of area postings requiring the skill that fit the seeker's preferences (%). */
    private Double preferenceSharePercent;

    /** Other skills that commonly appear in the same postings. */
    private List<String> relatedSkills = new ArrayList<>();

    /** Where the area postings requiring this skill are located. */
    private Map<String, Integer> byMunicipality = new java.util.LinkedHashMap<>();
    private Map<String, Integer> byEmploymentType = new java.util.LinkedHashMap<>();

    /** 0-100 weighted score, normalized over the criteria that could be calculated. */
    private double priorityScore;

    /** HIGH, MEDIUM or LOW. */
    private String priority;

    private double applicableWeight;
    private List<Map<String, Object>> factors = new ArrayList<>();

    private List<SimulationJobDto> sampleJobs = new ArrayList<>();

    /** True when the seeker has put this skill on their private learning list. */
    private boolean learningInterest;
}
