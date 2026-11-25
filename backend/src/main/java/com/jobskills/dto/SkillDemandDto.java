package com.jobskills.dto;

import lombok.Data;

import java.util.LinkedHashMap;
import java.util.Map;

/** One skill's demand in a filtered set of real active job postings. */
@Data
public class SkillDemandDto {
    private String skill;
    private String skillKey;

    /** Active postings in the filtered set that require this skill. */
    private int jobCount;

    /** jobCount / jobsWithSkills * 100. Null when no posting lists skills. */
    private Double sharePercent;

    /** Most common job category among the postings requiring the skill. */
    private String commonCategory;
    private String commonCategoryLabel;

    private Map<String, Integer> byMunicipality = new LinkedHashMap<>();
    private Map<String, Integer> byEmploymentType = new LinkedHashMap<>();

    /** How many seeker profiles record the skill (aggregate, never identities). */
    private int seekerCount;
}
