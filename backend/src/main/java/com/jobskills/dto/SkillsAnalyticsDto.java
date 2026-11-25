package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Aggregated skills analytics for administrators. No seeker identities. */
@Data
public class SkillsAnalyticsDto {
    private Map<String, Object> totals = new LinkedHashMap<>();
    private Map<String, Object> filters = new LinkedHashMap<>();

    private List<SkillDemandDto> mostRequired = new ArrayList<>();
    private Map<String, List<SkillDemandDto>> byMunicipality = new LinkedHashMap<>();
    private Map<String, List<SkillDemandDto>> byEmploymentType = new LinkedHashMap<>();
    private List<SupplyDemand> supplyDemand = new ArrayList<>();

    /**
     * JSM stores current job posts, not historical demand snapshots, so a trend
     * cannot be calculated honestly. This stays false and the interface shows
     * "Most Frequently Required" instead of claiming growth or decline.
     */
    private boolean trendAvailable = false;
    private String trendNote;

    private Map<String, String> methodology = new LinkedHashMap<>();

    @Data
    public static class SupplyDemand {
        private String skill;
        private String skillKey;

        /** Active postings that require the skill. */
        private int demandPostings;

        /** Seeker profiles that record the skill. */
        private int supplySeekers;

        /** demandPostings - supplySeekers. Positive means recorded supply is short. */
        private int gap;

        /** SHORTAGE, SURPLUS or BALANCED, derived from gap. */
        private String imbalance;

        /** Sum of available_positions on the postings; headcount, not unique postings. */
        private int positionsOffered;
    }
}
