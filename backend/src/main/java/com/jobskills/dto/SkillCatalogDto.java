package com.jobskills.dto;

import lombok.Data;

/** A normalized skill suggestion built from real postings and profiles. */
@Data
public class SkillCatalogDto {
    private String skill;
    private String skillKey;

    /** Active postings requiring the skill. */
    private int jobCount;

    /** Seeker profiles recording the skill (aggregate only). */
    private int seekerCount;

    /** Most common job category among postings requiring the skill. */
    private String commonCategory;
    private String commonCategoryLabel;

    /** Admin-entered, active learning resources for this skill. */
    private long verifiedResources;
}
