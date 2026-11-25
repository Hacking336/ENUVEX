package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** The complete seeker-facing skills gap analysis. */
@Data
public class SkillsAnalysisDto {
    private Map<String, Object> seeker = new LinkedHashMap<>();
    private Map<String, Object> totals = new LinkedHashMap<>();
    private Map<String, Object> filters = new LinkedHashMap<>();

    private int matchThreshold = 60;

    /** Skills already in the profile, with how many area jobs require each. */
    private List<SkillDemandDto> haveSkills = new ArrayList<>();

    /** Skills required by area jobs but missing from the profile, ranked. */
    private List<SkillPriorityDto> needSkills = new ArrayList<>();

    /** Most frequently required skills in the selected area. */
    private List<SkillDemandDto> mostInDemand = new ArrayList<>();

    /** Missing skills that currently unlock the most jobs. */
    private List<SkillPriorityDto> unlockSkills = new ArrayList<>();

    /** Honest explanation when there is nothing to calculate. */
    private List<String> notes = new ArrayList<>();
}
