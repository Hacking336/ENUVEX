package com.jobskills.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Career groupings and the jobs behind them, built only from active postings. */
@Data
public class CareerOpportunitiesDto {

    private Map<String, Object> totals = new LinkedHashMap<>();
    private List<Group> groups = new ArrayList<>();
    private List<String> notes = new ArrayList<>();

    @Data
    public static class Group {
        private String category;
        private String label;
        private int jobCount;
        private int evaluatedJobs;
        private Double averageMatchScore;

        private List<SkillDemandDto> topSkills = new ArrayList<>();
        private List<String> yourSkills = new ArrayList<>();
        private List<String> missingSkills = new ArrayList<>();

        private List<String> municipalities = new ArrayList<>();
        private List<Example> examples = new ArrayList<>();

        /** Job Search URL using the app's real filter parameters. */
        private String searchUrl;
    }

    @Data
    public static class Example {
        private Long id;
        private String jobTitle;
        private String companyName;
        private String municipality;
        private String barangay;
        private String employmentType;
        private String category;
        private Double matchPercentage;
        private String matchClassification;
        private String requiredSkills;
    }
}
