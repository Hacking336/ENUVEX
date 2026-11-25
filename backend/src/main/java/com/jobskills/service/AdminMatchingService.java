package com.jobskills.service;

import com.jobskills.dto.AdminMatchingStatsDto;
import com.jobskills.dto.MatchResultDto;
import com.jobskills.dto.ProfileCompletenessDto;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.JobPostRepository;
import com.jobskills.repository.JobSeekerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.*;

/**
 * Matching analytics for the admin console, counted from real rows.
 *
 * The averages are computed over every (active job, seeker profile) pair that
 * passes the filters. That is the honest definition of "match scores across
 * listings": one number per possible pairing, not an estimate. The number of
 * pairs is returned with the figures, and the work is capped so one request
 * cannot scan an unbounded combination of rows.
 */
@Service
@RequiredArgsConstructor
public class AdminMatchingService {

    /** Upper bounds for one analytics request. */
    private static final int MAX_JOBS = 200;
    private static final int MAX_SEEKERS = 200;

    private final JobPostRepository jobPostRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final MatchingEngine matchingEngine;

    public AdminMatchingStatsDto stats(Municipality municipality, String barangay,
                                       EmploymentType employmentType, LocalDate from, LocalDate to) {
        List<JobPost> jobs = jobPostRepository.findAll().stream()
            .filter(JobPost::isActive)
            .filter(job -> municipality == null || job.getMunicipality() == municipality)
            .filter(job -> barangay == null || barangay.isBlank()
                || (job.getBarangay() != null && job.getBarangay().equalsIgnoreCase(barangay.trim())))
            .filter(job -> employmentType == null || job.getEmploymentType() == employmentType)
            .filter(job -> from == null || (job.getCreatedAt() != null
                && !job.getCreatedAt().toLocalDate().isBefore(from)))
            .filter(job -> to == null || (job.getCreatedAt() != null
                && !job.getCreatedAt().toLocalDate().isAfter(to)))
            .sorted(Comparator.comparing(JobPost::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())))
            .limit(MAX_JOBS)
            .toList();

        List<JobSeekerProfile> seekers = seekerRepository.findAll().stream()
            .limit(MAX_SEEKERS)
            .toList();

        AdminMatchingStatsDto stats = new AdminMatchingStatsDto();
        stats.setActiveJobs(jobs.size());
        stats.setJobsWithRequiredSkills(jobs.stream()
            .filter(job -> job.getRequiredSkills() != null && !job.getRequiredSkills().isBlank())
            .count());
        stats.setJobsWithoutRequiredSkills(stats.getActiveJobs() - stats.getJobsWithRequiredSkills());
        stats.setJobSeekers(seekers.size());

        long completeProfiles = 0;
        for (JobSeekerProfile seeker : seekers) {
            ProfileCompletenessDto completeness = matchingEngine.profileCompleteness(seeker);
            if (completeness.getPercentage() >= 80) {
                completeProfiles++;
            }
        }
        stats.setJobSeekersWithCompleteProfiles(completeProfiles);

        Map<String, Long> matchedSkills = new HashMap<>();
        Map<String, Long> missingSkills = new HashMap<>();
        Map<Long, JobAccumulator> perJob = new LinkedHashMap<>();
        long[] bands = new long[5];
        double scoreSum = 0;
        double weightSum = 0;
        long pairs = 0;

        for (JobPost job : jobs) {
            JobAccumulator accumulator = new JobAccumulator(job);
            for (JobSeekerProfile seeker : seekers) {
                MatchResultDto match = matchingEngine.calculateMatch(seeker, job);
                pairs++;
                scoreSum += match.getOverallMatch();
                weightSum += match.getApplicableWeight();
                bands[bandIndex(match.getOverallMatch())]++;
                accumulator.add(match);

                for (String skill : match.getMatchedSkills()) {
                    matchedSkills.merge(skill, 1L, Long::sum);
                }
                for (String skill : match.getMissingSkills()) {
                    missingSkills.merge(skill, 1L, Long::sum);
                }
            }
            perJob.put(job.getId(), accumulator);
        }

        stats.setPairsEvaluated(pairs);
        stats.setAverageMatchScore(pairs == 0 ? null : round1(scoreSum / pairs));
        stats.setAverageApplicableWeight(pairs == 0 ? null : round1(weightSum / pairs));
        stats.setMostMatchedSkills(topSkills(matchedSkills, 10));
        stats.setMostMissingSkills(topSkills(missingSkills, 10));
        stats.setDistribution(distribution(bands));
        stats.setTopJobs(perJob.values().stream()
            .filter(accumulator -> accumulator.count > 0)
            .sorted(Comparator.comparingLong((JobAccumulator a) -> a.strongMatches).reversed()
                .thenComparingDouble(a -> a.average()))
            .limit(5)
            .map(JobAccumulator::toDto)
            .toList());
        return stats;
    }

    private static int bandIndex(double score) {
        if (score >= 90) return 0;
        if (score >= 75) return 1;
        if (score >= 60) return 2;
        if (score >= 40) return 3;
        return 4;
    }

    private static List<AdminMatchingStatsDto.Bucket> distribution(long[] bands) {
        String[] labels = {"Excellent (90–100%)", "Strong (75–89%)", "Good (60–74%)",
            "Partial (40–59%)", "Low (below 40%)"};
        int[] mins = {90, 75, 60, 40, 0};
        int[] maxs = {100, 89, 74, 59, 39};
        List<AdminMatchingStatsDto.Bucket> buckets = new ArrayList<>();
        for (int i = 0; i < labels.length; i++) {
            buckets.add(new AdminMatchingStatsDto.Bucket(labels[i], mins[i], maxs[i], bands[i]));
        }
        return buckets;
    }

    private static List<AdminMatchingStatsDto.SkillCount> topSkills(Map<String, Long> counts, int limit) {
        return counts.entrySet().stream()
            .sorted(Map.Entry.<String, Long>comparingByValue().reversed()
                .thenComparing(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER)))
            .limit(limit)
            .map(entry -> new AdminMatchingStatsDto.SkillCount(entry.getKey(), entry.getValue()))
            .toList();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    private static final class JobAccumulator {
        private final JobPost job;
        private long count;
        private long strongMatches;
        private double scoreSum;

        private JobAccumulator(JobPost job) {
            this.job = job;
        }

        private void add(MatchResultDto match) {
            count++;
            scoreSum += match.getOverallMatch();
            if (match.getOverallMatch() >= 60) {
                strongMatches++;
            }
        }

        private double average() {
            return count == 0 ? 0 : round1(scoreSum / count);
        }

        private AdminMatchingStatsDto.JobMatchStat toDto() {
            return new AdminMatchingStatsDto.JobMatchStat(
                job.getId(),
                job.getJobTitle(),
                job.getEmployer() != null ? job.getEmployer().getBusinessName() : null,
                job.getMunicipality() != null ? job.getMunicipality().name() : null,
                strongMatches,
                average());
        }
    }
}
