package com.jobskills.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Matching analytics for the admin console.
 *
 * Every number is counted from the job, seeker and profile rows in the database
 * for the selected filters; nothing is estimated or invented. {@code pairs}
 * records how many job/seeker combinations were scored so the averages can be
 * read with their real sample size.
 */
@Data
public class AdminMatchingStatsDto {

    private long activeJobs;
    private long jobsWithRequiredSkills;
    private long jobsWithoutRequiredSkills;
    private long jobSeekers;
    private long jobSeekersWithCompleteProfiles;
    private long pairsEvaluated;

    /** Average final match percentage across the evaluated pairs. */
    private Double averageMatchScore;

    /** Average share of the weights that could actually be judged. */
    private Double averageApplicableWeight;

    private List<SkillCount> mostMatchedSkills = new ArrayList<>();
    private List<SkillCount> mostMissingSkills = new ArrayList<>();
    private List<JobMatchStat> topJobs = new ArrayList<>();
    private List<Bucket> distribution = new ArrayList<>();

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SkillCount {
        private String skill;
        private long count;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobMatchStat {
        private Long jobId;
        private String jobTitle;
        private String companyName;
        private String municipality;
        private long strongMatches;
        private double averageScore;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Bucket {
        private String label;
        private int min;
        private int max;
        private long count;
    }
}
