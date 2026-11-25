package com.jobskills.service;

import com.jobskills.dto.*;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.LearningResource;
import com.jobskills.model.SkillLearningInterest;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.JobSeekerProfileRepository;
import com.jobskills.repository.LearningResourceRepository;
import com.jobskills.repository.SkillLearningInterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * Data-driven skills gap analysis. Every recommendation comes from active job
 * postings and the seeker's own profile; nothing is invented, sampled or
 * randomized. The official match score always comes from
 * {@link MatchingEngine#calculateMatch}, and simulations run that same engine
 * against a throw-away copy of the profile.
 */
@Service
@RequiredArgsConstructor
public class SkillsGapService {

    private final SkillsDemandService demandService;
    private final MatchingEngine matchingEngine;
    private final JobSeekerProfileRepository seekerRepository;
    private final SkillLearningInterestRepository interestRepository;
    private final LearningResourceRepository resourceRepository;

    private static final int DEFAULT_THRESHOLD = 60;
    private static final double WEIGHT_DEMAND = 50.0;
    private static final double WEIGHT_IMPACT = 30.0;
    private static final double WEIGHT_PREFERENCE = 20.0;

    /* ================= analysis ================= */

    @Transactional(readOnly = true)
    public SkillsAnalysisDto analyse(Long userId, Municipality municipality, String barangay,
                                     EmploymentType employmentType, JobCategory category, Integer days) {
        JobSeekerProfile seeker = requireProfile(userId);
        List<JobPost> allJobs = demandService.activeJobs();

        Municipality area = municipality != null ? municipality : seeker.getMunicipality();
        List<JobPost> areaJobs = demandService.filter(allJobs, area, barangay, employmentType, category, days);
        Map<String, List<JobPost>> areaBySkill = demandService.bySkill(areaJobs);
        Map<String, Integer> supply = demandService.seekerSupply();

        List<String> mySkills = displaySkills(seeker.getSkills());
        Set<String> have = new HashSet<>();
        for (String skill : mySkills) have.add(MatchingEngine.normalizeSkill(skill));

        Set<String> interestKeys = new HashSet<>();
        for (SkillLearningInterest interest : interestRepository.findBySeekerIdOrderByCreatedAtDesc(seeker.getId())) {
            interestKeys.add(interest.getSkillKey());
        }

        Map<Long, Double> currentScores = new HashMap<>();
        List<SkillPriorityDto> priorities = buildPriorities(seeker, areaJobs, allJobs, areaBySkill,
            currentScores, interestKeys, DEFAULT_THRESHOLD);

        List<SkillPriorityDto> need = priorities.stream()
            .filter(row -> !have.contains(row.getSkillKey()))
            .limit(20)
            .toList();

        List<SkillPriorityDto> unlock = priorities.stream()
            .filter(row -> row.getOpportunityJobs() > 0)
            .sorted(Comparator.comparingInt(SkillPriorityDto::getOpportunityJobs).reversed()
                .thenComparing(Comparator.comparingDouble(SkillPriorityDto::getPriorityScore).reversed()))
            .limit(5)
            .toList();

        SkillsAnalysisDto dto = new SkillsAnalysisDto();
        dto.setMatchThreshold(DEFAULT_THRESHOLD);

        Map<String, Object> seekerInfo = new LinkedHashMap<>();
        seekerInfo.put("fullName", seeker.getFullName());
        seekerInfo.put("municipality", seeker.getMunicipality());
        seekerInfo.put("municipalityLabel", SkillsDemandService.municipalityLabel(seeker.getMunicipality()));
        seekerInfo.put("barangay", seeker.getBarangay());
        seekerInfo.put("employmentTypePreference", seeker.getEmploymentTypePreference());
        seekerInfo.put("employmentTypePreferenceLabel",
            seeker.getEmploymentTypePreference() == null ? null
                : SkillsDemandService.typeLabel(seeker.getEmploymentTypePreference()));
        seekerInfo.put("availability", seeker.getAvailability());
        seekerInfo.put("skills", mySkills);
        dto.setSeeker(seekerInfo);

        int jobsWithSkills = demandService.jobsWithSkills(areaJobs);
        int currentMatching = 0;
        double matchSum = 0;
        int matchCount = 0;
        for (JobPost job : areaJobs) {
            if (MatchingEngine.splitSkills(job.getRequiredSkills()).isEmpty()) continue;
            double score = currentScore(currentScores, seeker, job);
            matchSum += score;
            matchCount++;
            if (score >= DEFAULT_THRESHOLD) currentMatching++;
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("allActiveJobs", allJobs.size());
        totals.put("areaJobs", areaJobs.size());
        totals.put("jobsWithSkills", jobsWithSkills);
        totals.put("jobsWithoutSkills", areaJobs.size() - jobsWithSkills);
        totals.put("mySkills", mySkills.size());
        totals.put("skillsRequiredInArea", areaBySkill.size());
        totals.put("missingSkills", need.size());
        totals.put("currentMatchingJobs", currentMatching);
        totals.put("averageMatchScore", matchCount == 0 ? null
            : SkillsDemandService.round1(matchSum / matchCount));
        totals.put("jobsEvaluated", matchCount);
        dto.setTotals(totals);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("municipality", area);
        filters.put("municipalityLabel", SkillsDemandService.municipalityLabel(area));
        filters.put("barangay", barangay == null || barangay.isBlank() ? null : barangay.trim());
        filters.put("employmentType", employmentType);
        filters.put("employmentTypeLabel", employmentType == null ? null
            : SkillsDemandService.typeLabel(employmentType));
        filters.put("category", category);
        filters.put("categoryLabel", category == null ? null : SkillsDemandService.categoryLabel(category));
        filters.put("days", days);
        filters.put("defaultedMunicipality", municipality == null);
        dto.setFilters(filters);

        List<SkillDemandDto> haveRows = new ArrayList<>();
        for (String skill : mySkills) {
            String key = MatchingEngine.normalizeSkill(skill);
            SkillDemandDto row = new SkillDemandDto();
            row.setSkill(skill);
            row.setSkillKey(key);
            row.setJobCount(areaBySkill.getOrDefault(key, List.of()).size());
            row.setSharePercent(jobsWithSkills == 0 ? null
                : SkillsDemandService.round1(row.getJobCount() * 100.0 / jobsWithSkills));
            row.setSeekerCount(supply.getOrDefault(key, 0));
            List<JobPost> requiring = areaBySkill.getOrDefault(key, List.of());
            if (!requiring.isEmpty()) {
                Map<String, Integer> municipalities = new LinkedHashMap<>();
                Map<String, Integer> types = new LinkedHashMap<>();
                for (JobPost job : requiring) {
                    municipalities.merge(SkillsDemandService.municipalityLabel(job.getMunicipality()), 1, Integer::sum);
                    types.merge(SkillsDemandService.typeLabel(job.getEmploymentType()), 1, Integer::sum);
                }
                row.setByMunicipality(municipalities);
                row.setByEmploymentType(types);
                row.setCommonCategoryLabel(SkillsDemandService.categoryLabel(requiring.get(0).getJobCategory()));
                row.setCommonCategory(requiring.get(0).getJobCategory() == null ? null
                    : requiring.get(0).getJobCategory().name());
            }
            haveRows.add(row);
        }
        haveRows.sort(Comparator.comparingInt(SkillDemandDto::getJobCount).reversed()
            .thenComparing(SkillDemandDto::getSkill, String.CASE_INSENSITIVE_ORDER));
        dto.setHaveSkills(haveRows);
        dto.setNeedSkills(need);
        dto.setUnlockSkills(unlock);
        dto.setMostInDemand(demandService.summarize(areaJobs, supply, 10));

        if (allJobs.isEmpty()) {
            dto.getNotes().add("There are no active job postings yet, so no skill demand can be calculated.");
        } else if (areaJobs.isEmpty()) {
            dto.getNotes().add("No active job postings match the selected area and filters. Widen the filters to see demand.");
        } else if (jobsWithSkills == 0) {
            dto.getNotes().add("The postings in this area do not list required skills, so no gap can be calculated from them.");
        } else if (mySkills.isEmpty()) {
            dto.getNotes().add("Your profile lists no skills yet. Add the skills you genuinely have, then review the gaps below.");
        } else if (need.isEmpty()) {
            dto.getNotes().add("Your profile already covers every skill that active postings in this area require.");
        }
        return dto;
    }

    /* ================= priority model ================= */

    private List<SkillPriorityDto> buildPriorities(JobSeekerProfile seeker, List<JobPost> areaJobs,
                                                   List<JobPost> allJobs, Map<String, List<JobPost>> areaBySkill,
                                                   Map<Long, Double> currentScores, Set<String> interestKeys,
                                                   int threshold) {
        Map<String, List<JobPost>> allBySkill = demandService.bySkill(allJobs);
        int areaJobsWithSkills = demandService.jobsWithSkills(areaJobs);
        Set<String> have = new HashSet<>();
        for (String skill : MatchingEngine.splitSkills(seeker.getSkills())) {
            have.add(MatchingEngine.normalizeSkill(skill));
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<JobPost>> entry : areaBySkill.entrySet()) {
            if (have.contains(entry.getKey())) continue;
            List<JobPost> requiring = entry.getValue();
            String display = demandService.displayName(entry.getKey(), requiring);
            JobSeekerProfile simulated = copyWithSkills(seeker, List.of(display));

            int opportunity = 0;
            int improved = 0;
            int below = 0;
            List<SimulationJobDto> samples = new ArrayList<>();
            for (JobPost job : requiring) {
                double current = currentScore(currentScores, seeker, job);
                MatchResultDto potential = matchingEngine.calculateMatch(simulated, job);
                double potentialScore = potential.getOverallMatch();
                boolean newly = current < threshold && potentialScore >= threshold;
                if (newly) opportunity++;
                if (potentialScore > current + 0.05) {
                    improved++;
                    if (!newly) below++;
                    samples.add(toSimulationJob(job, matchingEngine.calculateMatch(seeker, job), potential, threshold));
                }
            }
            samples.sort(Comparator.comparingDouble(SimulationJobDto::getDelta).reversed());
            if (samples.size() > 3) samples = new ArrayList<>(samples.subList(0, 3));

            Double preferenceShare = preferenceShare(seeker, requiring);
            Double sharePercent = areaJobsWithSkills == 0 ? null
                : requiredShare(requiring, areaJobsWithSkills);

            Map<String, Integer> relatedCounts = new LinkedHashMap<>();
            Map<String, String> relatedNames = new LinkedHashMap<>();
            for (JobPost job : requiring) {
                for (String skill : MatchingEngine.splitSkills(job.getRequiredSkills())) {
                    String relatedKey = MatchingEngine.normalizeSkill(skill);
                    if (relatedKey.isEmpty() || relatedKey.equals(entry.getKey())) continue;
                    relatedCounts.merge(relatedKey, 1, Integer::sum);
                    relatedNames.putIfAbsent(relatedKey, skill.trim());
                }
            }
            List<String> related = relatedCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(5)
                .map(relatedEntry -> relatedNames.getOrDefault(relatedEntry.getKey(), relatedEntry.getKey()))
                .toList();

            candidates.add(new Candidate(entry.getKey(), display, requiring.size(),
                allBySkill.getOrDefault(entry.getKey(), List.of()).size(),
                opportunity, improved, below, preferenceShare, sharePercent, related, samples));
        }

        int maxArea = 0;
        int maxOpportunity = 0;
        for (Candidate candidate : candidates) {
            maxArea = Math.max(maxArea, candidate.areaJobs);
            maxOpportunity = Math.max(maxOpportunity, candidate.opportunity);
        }

        List<SkillPriorityDto> rows = new ArrayList<>();
        for (Candidate candidate : candidates) {
            List<Map<String, Object>> factors = new ArrayList<>();
            double applicable = 0;
            double earned = 0;

            if (maxArea > 0) {
                double score = candidate.areaJobs * 100.0 / maxArea;
                applicable += WEIGHT_DEMAND;
                earned += WEIGHT_DEMAND * score / 100.0;
                factors.add(factor("localDemand", "Local demand", WEIGHT_DEMAND, candidate.areaJobs,
                    candidate.areaJobs + " active job posting" + (candidate.areaJobs == 1 ? "" : "s")
                        + " in your selected area require this skill",
                    SkillsDemandService.round1(score)));
            }
            if (maxOpportunity > 0) {
                double score = candidate.opportunity * 100.0 / maxOpportunity;
                applicable += WEIGHT_IMPACT;
                earned += WEIGHT_IMPACT * score / 100.0;
                factors.add(factor("opportunityImpact", "Job opportunity impact", WEIGHT_IMPACT,
                    candidate.opportunity,
                    candidate.opportunity + " job" + (candidate.opportunity == 1 ? "" : "s")
                        + " would reach the " + threshold + "% match line if you had this skill",
                    SkillsDemandService.round1(score)));
            }
            if (candidate.preferenceShare != null) {
                applicable += WEIGHT_PREFERENCE;
                earned += WEIGHT_PREFERENCE * candidate.preferenceShare / 100.0;
                factors.add(factor("preferenceRelevance", "Preference relevance", WEIGHT_PREFERENCE,
                    SkillsDemandService.round1(candidate.preferenceShare),
                    Math.round(candidate.preferenceShare) + "% of the postings requiring it are in your municipality"
                        + " or your preferred employment type",
                    SkillsDemandService.round1(candidate.preferenceShare)));
            }

            double priorityScore = applicable > 0 ? earned / applicable * 100.0 : 0;
            SkillPriorityDto row = new SkillPriorityDto();
            row.setSkill(candidate.display);
            row.setSkillKey(candidate.key);
            row.setAreaJobCount(candidate.areaJobs);
            row.setTotalJobCount(candidate.totalJobs);
            row.setOpportunityJobs(candidate.opportunity);
            row.setImprovedJobs(candidate.improved);
            row.setImprovedButBelowThreshold(candidate.below);
            row.setSharePercent(candidate.sharePercent);
            row.setPreferenceSharePercent(candidate.preferenceShare == null ? null
                : SkillsDemandService.round1(candidate.preferenceShare));
            row.setRelatedSkills(candidate.relatedSkills);
            row.setByMunicipality(demandService.breakdown(areaBySkill.get(candidate.key), true));
            row.setByEmploymentType(demandService.breakdown(areaBySkill.get(candidate.key), false));
            row.setPriorityScore(SkillsDemandService.round1(priorityScore));
            row.setPriority(priorityScore >= 65 ? "HIGH" : priorityScore >= 35 ? "MEDIUM" : "LOW");
            row.setApplicableWeight(applicable);
            row.setFactors(factors);
            row.setSampleJobs(candidate.samples);
            row.setLearningInterest(interestKeys.contains(candidate.key));
            rows.add(row);
        }

        rows.sort(Comparator.comparingDouble(SkillPriorityDto::getPriorityScore).reversed()
            .thenComparing(Comparator.comparingInt(SkillPriorityDto::getOpportunityJobs).reversed())
            .thenComparing(Comparator.comparingInt(SkillPriorityDto::getAreaJobCount).reversed())
            .thenComparing(SkillPriorityDto::getSkill, String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private Double preferenceShare(JobSeekerProfile seeker, List<JobPost> jobs) {
        if (jobs.isEmpty()) return null;
        if (seeker.getMunicipality() == null && seeker.getEmploymentTypePreference() == null) return null;
        int fit = 0;
        for (JobPost job : jobs) {
            boolean sameMunicipality = seeker.getMunicipality() != null
                && job.getMunicipality() == seeker.getMunicipality();
            boolean sameType = seeker.getEmploymentTypePreference() != null
                && job.getEmploymentType() == seeker.getEmploymentTypePreference();
            if (sameMunicipality || sameType) fit++;
        }
        return fit * 100.0 / jobs.size();
    }

    private Map<String, Object> factor(String key, String label, double weight, Number raw,
                                       String explanation, double score) {
        Map<String, Object> factor = new LinkedHashMap<>();
        factor.put("key", key);
        factor.put("label", label);
        factor.put("weight", weight);
        factor.put("raw", raw);
        factor.put("score", score);
        factor.put("explanation", explanation);
        return factor;
    }

    private record Candidate(String key, String display, int areaJobs, int totalJobs,
                             int opportunity, int improved, int below, Double preferenceShare,
                             Double sharePercent, List<String> relatedSkills,
                             List<SimulationJobDto> samples) {
    }

    private Double requiredShare(List<JobPost> requiring, int jobsWithSkills) {
        return jobsWithSkills == 0 ? null
            : SkillsDemandService.round1(requiring.size() * 100.0 / jobsWithSkills);
    }

    /* ================= simulator ================= */

    @Transactional(readOnly = true)
    public SimulationResultDto simulate(Long userId, SimulationRequest request) {
        JobSeekerProfile seeker = requireProfile(userId);
        int threshold = request != null && request.getThreshold() != null
            ? Math.max(1, Math.min(100, request.getThreshold())) : DEFAULT_THRESHOLD;

        Set<String> have = new HashSet<>();
        for (String skill : MatchingEngine.splitSkills(seeker.getSkills())) {
            have.add(MatchingEngine.normalizeSkill(skill));
        }

        List<String> added = new ArrayList<>();
        Set<String> addedKeys = new HashSet<>();
        if (request != null && request.getSkills() != null) {
            for (String raw : request.getSkills()) {
                if (raw == null || raw.isBlank()) continue;
                String cleaned = raw.replaceAll("\\s+", " ").trim();
                if (cleaned.length() > 100) throw new IllegalArgumentException("A skill name is too long.");
                String key = MatchingEngine.normalizeSkill(cleaned);
                if (key.isEmpty() || have.contains(key) || !addedKeys.add(key)) continue;
                added.add(cleaned);
                if (added.size() >= 10) break;
            }
        }
        if (added.isEmpty()) {
            throw new IllegalArgumentException(
                "Select at least one skill that is not already in your profile to simulate.");
        }

        JobSeekerProfile simulated = copyWithSkills(seeker, added);
        List<JobPost> jobs = demandService.activeJobs();
        Map<Long, Double> currentCache = new HashMap<>();

        int currentMatching = 0;
        int potentialMatching = 0;
        double currentSum = 0;
        double potentialSum = 0;
        int evaluated = 0;
        List<SimulationJobDto> rows = new ArrayList<>();
        List<SimulationJobDto> unlocked = new ArrayList<>();
        List<SimulationJobDto> improved = new ArrayList<>();
        List<SimulationJobDto> improvedBelow = new ArrayList<>();
        Map<String, Integer> gaps = new LinkedHashMap<>();

        for (JobPost job : jobs) {
            MatchResultDto current = matchingEngine.calculateMatch(seeker, job);
            MatchResultDto potential = matchingEngine.calculateMatch(simulated, job);
            SimulationJobDto row = toSimulationJob(job, current, potential, threshold);
            rows.add(row);

            if (row.isNewlyQualified()) {
                unlocked.add(row);
                for (String missing : row.getRemainingMissingSkills()) {
                    gaps.merge(missing, 1, Integer::sum);
                }
            }
            if (row.getDelta() > 0.05) improved.add(row);
            if (row.isImprovedButBelowThreshold()) improvedBelow.add(row);

            if (current.getTotalRequiredSkills() > 0) {
                evaluated++;
                currentSum += current.getOverallMatch();
                potentialSum += potential.getOverallMatch();
            }
            if (current.getOverallMatch() >= threshold) currentMatching++;
            if (potential.getOverallMatch() >= threshold) potentialMatching++;
        }

        rows.sort(Comparator.comparing(SimulationJobDto::isNewlyQualified).reversed()
            .thenComparing(Comparator.comparingDouble(SimulationJobDto::getDelta).reversed()));
        unlocked.sort(Comparator.comparingDouble(SimulationJobDto::getDelta).reversed());
        improved.sort(Comparator.comparingDouble(SimulationJobDto::getDelta).reversed());

        SimulationResultDto result = new SimulationResultDto();
        result.setThreshold(threshold);
        result.setAddedSkills(added);
        result.setCurrentMatchingJobs(currentMatching);
        result.setPotentialMatchingJobs(potentialMatching);
        result.setAdditionalMatchingJobs(Math.max(0, potentialMatching - currentMatching));
        result.setCurrentAverageScore(evaluated == 0 ? 0 : SkillsDemandService.round1(currentSum / evaluated));
        result.setPotentialAverageScore(evaluated == 0 ? 0 : SkillsDemandService.round1(potentialSum / evaluated));
        result.setAverageScoreGain(SkillsDemandService.round1(result.getPotentialAverageScore()
            - result.getCurrentAverageScore()));
        result.setJobsEvaluated(evaluated);
        result.setNewlyUnlocked(unlocked);
        result.setImproved(improved);
        result.setImprovedButBelowThreshold(improvedBelow);
        result.setJobs(rows);

        List<Map<String, Object>> gapRows = new ArrayList<>();
        gaps.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .limit(8)
            .forEach(entry -> {
                Map<String, Object> gap = new LinkedHashMap<>();
                gap.put("skill", entry.getKey());
                gap.put("jobs", entry.getValue());
                gapRows.add(gap);
            });
        result.setRemainingGaps(gapRows);

        result.setExplanation(jobs.isEmpty()
            ? "There are no active jobs to simulate against yet."
            : "Simulation only: " + added.size() + " hypothetical skill"
                + (added.size() == 1 ? "" : "s") + " added to a copy of your profile. "
                + "The official match score is unchanged. Where other requirements are still unmet, "
                + "a job is not shown as newly qualified.");
        return result;
    }

    private SimulationJobDto toSimulationJob(JobPost job, MatchResultDto current,
                                             MatchResultDto potential, int threshold) {
        SimulationJobDto row = new SimulationJobDto();
        row.setId(job.getId());
        row.setJobTitle(job.getJobTitle());
        row.setCompanyName(job.getEmployer() == null ? null : job.getEmployer().getBusinessName());
        row.setMunicipality(job.getMunicipality() == null ? null : job.getMunicipality().name());
        row.setBarangay(job.getBarangay());
        row.setEmploymentType(job.getEmploymentType() == null ? null : job.getEmploymentType().name());
        row.setCategory(job.getJobCategory() == null ? null : job.getJobCategory().name());
        row.setCurrentScore(current.getOverallMatch());
        row.setCurrentClassification(current.getClassification());
        row.setPotentialScore(potential.getOverallMatch());
        row.setPotentialClassification(potential.getClassification());
        row.setDelta(SkillsDemandService.round1(potential.getOverallMatch() - current.getOverallMatch()));
        row.setNewlyQualified(current.getOverallMatch() < threshold && potential.getOverallMatch() >= threshold);
        row.setImprovedButBelowThreshold(row.getDelta() > 0.05 && potential.getOverallMatch() < threshold);
        row.setRemainingMissingSkills(new ArrayList<>(potential.getMissingSkills()));

        boolean unmet = !potential.isComplete();
        for (MatchCriterionDto criterion : potential.getCriteria()) {
            if (!"skills".equals(criterion.getKey()) && criterion.isApplicable()
                && !"MATCH".equals(criterion.getStatus())) {
                unmet = true;
            }
        }
        row.setOtherRequirementsIncomplete(unmet);
        return row;
    }

    /* ================= careers ================= */

    @Transactional(readOnly = true)
    public CareerOpportunitiesDto careers(Long userId, Municipality municipality, JobCategory category) {
        JobSeekerProfile seeker = requireProfile(userId);
        List<JobPost> jobs = demandService.filter(demandService.activeJobs(), municipality, null, null, category, null);
        Map<String, Integer> supply = demandService.seekerSupply();

        Set<String> have = new HashSet<>();
        for (String skill : MatchingEngine.splitSkills(seeker.getSkills())) {
            have.add(MatchingEngine.normalizeSkill(skill));
        }

        Map<JobCategory, List<JobPost>> groups = new EnumMap<>(JobCategory.class);
        for (JobPost job : jobs) {
            if (job.getJobCategory() == null) continue;
            groups.computeIfAbsent(job.getJobCategory(), ignored -> new ArrayList<>()).add(job);
        }

        CareerOpportunitiesDto dto = new CareerOpportunitiesDto();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("activeJobs", jobs.size());
        totals.put("groups", groups.size());
        totals.put("municipality", municipality);
        totals.put("municipalityLabel", municipality == null ? null
            : SkillsDemandService.municipalityLabel(municipality));
        dto.setTotals(totals);

        List<CareerOpportunitiesDto.Group> result = new ArrayList<>();
        for (Map.Entry<JobCategory, List<JobPost>> entry : groups.entrySet()) {
            List<JobPost> groupJobs = entry.getValue();
            CareerOpportunitiesDto.Group group = new CareerOpportunitiesDto.Group();
            group.setCategory(entry.getKey().name());
            group.setLabel(SkillsDemandService.categoryLabel(entry.getKey()));
            group.setJobCount(groupJobs.size());

            List<SkillDemandDto> topSkills = demandService.summarize(groupJobs, supply, 6);
            group.setTopSkills(topSkills);

            List<String> yourSkills = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            Set<String> relevant = new HashSet<>();
            for (SkillDemandDto skill : topSkills) relevant.add(skill.getSkillKey());
            for (String skill : MatchingEngine.splitSkills(seeker.getSkills())) {
                if (relevant.contains(MatchingEngine.normalizeSkill(skill))) yourSkills.add(skill);
            }
            for (SkillDemandDto skill : topSkills) {
                if (!have.contains(skill.getSkillKey())) missing.add(skill.getSkill());
            }
            group.setYourSkills(yourSkills);
            group.setMissingSkills(missing);

            double matchSum = 0;
            int evaluated = 0;
            List<CareerOpportunitiesDto.Example> examples = new ArrayList<>();
            Set<String> municipalities = new TreeSet<>();
            for (JobPost job : groupJobs) {
                MatchResultDto match = matchingEngine.calculateMatch(seeker, job);
                matchSum += match.getOverallMatch();
                evaluated++;
                if (job.getMunicipality() != null) {
                    municipalities.add(SkillsDemandService.municipalityLabel(job.getMunicipality()));
                }
                if (examples.size() < 3) {
                    CareerOpportunitiesDto.Example example = new CareerOpportunitiesDto.Example();
                    example.setId(job.getId());
                    example.setJobTitle(job.getJobTitle());
                    example.setCompanyName(job.getEmployer() == null ? null : job.getEmployer().getBusinessName());
                    example.setMunicipality(job.getMunicipality() == null ? null : job.getMunicipality().name());
                    example.setBarangay(job.getBarangay());
                    example.setEmploymentType(job.getEmploymentType() == null ? null : job.getEmploymentType().name());
                    example.setCategory(job.getJobCategory().name());
                    example.setMatchPercentage(match.getOverallMatch());
                    example.setMatchClassification(match.getClassification());
                    example.setRequiredSkills(job.getRequiredSkills());
                    examples.add(example);
                }
            }
            examples.sort(Comparator.comparing(CareerOpportunitiesDto.Example::getMatchPercentage,
                Comparator.nullsLast(Comparator.reverseOrder())));
            group.setExamples(examples);
            group.setEvaluatedJobs(evaluated);
            group.setAverageMatchScore(evaluated == 0 ? null : SkillsDemandService.round1(matchSum / evaluated));
            group.setMunicipalities(new ArrayList<>(municipalities));

            StringBuilder url = new StringBuilder("jobs.html?category=").append(entry.getKey().name());
            if (municipality != null) url.append("&municipality=").append(municipality.name());
            group.setSearchUrl(url.toString());
            result.add(group);
        }

        result.sort(Comparator.comparing(CareerOpportunitiesDto.Group::getAverageMatchScore,
                Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(Comparator.comparingInt(CareerOpportunitiesDto.Group::getJobCount).reversed()));
        dto.setGroups(result);
        if (jobs.isEmpty()) {
            dto.getNotes().add("No active job postings match these filters, so no career groups can be shown.");
        } else if (result.isEmpty()) {
            dto.getNotes().add("Active postings in this area do not have a job category recorded, so they cannot be grouped.");
        }
        return dto;
    }

    /* ================= catalog and learning ================= */

    @Transactional(readOnly = true)
    public List<SkillCatalogDto> catalog(String query, Integer limitParam) {
        int limit = limitParam == null ? 20 : Math.max(1, Math.min(50, limitParam));
        List<JobPost> jobs = demandService.activeJobs();
        Map<String, List<JobPost>> bySkill = demandService.bySkill(jobs);

        Map<String, Integer> supply = demandService.seekerSupply();
        Map<String, String> seekerSpellings = seekerSpellings();
        Map<String, Long> resources = new HashMap<>();
        for (LearningResource resource : resourceRepository.findAll()) {
            if (resource.isActive()) resources.merge(resource.getSkillKey(), 1L, Long::sum);
        }

        Set<String> keys = new LinkedHashSet<>(bySkill.keySet());
        keys.addAll(supply.keySet());

        String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        List<SkillCatalogDto> rows = new ArrayList<>();
        for (String key : keys) {
            List<JobPost> requiring = bySkill.getOrDefault(key, List.of());
            String display = requiring.isEmpty()
                ? seekerSpellings.getOrDefault(key, key)
                : demandService.displayName(key, requiring);
            if (!needle.isEmpty() && !display.toLowerCase(Locale.ROOT).contains(needle)) continue;

            SkillCatalogDto row = new SkillCatalogDto();
            row.setSkill(display);
            row.setSkillKey(key);
            row.setJobCount(requiring.size());
            row.setSeekerCount(supply.getOrDefault(key, 0));
            Map<JobCategory, Integer> categories = new EnumMap<>(JobCategory.class);
            for (JobPost job : requiring) {
                if (job.getJobCategory() != null) categories.merge(job.getJobCategory(), 1, Integer::sum);
            }
            categories.entrySet().stream().max(Map.Entry.comparingByValue()).ifPresent(best -> {
                row.setCommonCategory(best.getKey().name());
                row.setCommonCategoryLabel(SkillsDemandService.categoryLabel(best.getKey()));
            });
            row.setVerifiedResources(resources.getOrDefault(key, 0L));
            rows.add(row);
        }

        rows.sort(Comparator.comparingInt(SkillCatalogDto::getJobCount).reversed()
            .thenComparing(Comparator.comparingInt(SkillCatalogDto::getSeekerCount).reversed())
            .thenComparing(SkillCatalogDto::getSkill, String.CASE_INSENSITIVE_ORDER));
        return rows.size() > limit ? new ArrayList<>(rows.subList(0, limit)) : rows;
    }

    @Transactional(readOnly = true)
    public List<LearningResource> resourcesForSkill(String skill) {
        String key = MatchingEngine.normalizeSkill(skill);
        if (key.isEmpty()) throw new IllegalArgumentException("A skill is required.");
        return resourceRepository.findByActiveTrueAndSkillKeyOrderByTitleAsc(key);
    }

    @Transactional(readOnly = true)
    public List<LearningInterestDto> interests(Long userId) {
        JobSeekerProfile seeker = requireProfile(userId);
        List<LearningInterestDto> rows = new ArrayList<>();
        for (SkillLearningInterest interest : interestRepository.findBySeekerIdOrderByCreatedAtDesc(seeker.getId())) {
            LearningInterestDto dto = new LearningInterestDto();
            dto.setId(interest.getId());
            dto.setSkill(interest.getSkillName());
            dto.setSkillKey(interest.getSkillKey());
            dto.setCreatedAt(interest.getCreatedAt());
            rows.add(dto);
        }
        return rows;
    }

    @Transactional
    public LearningInterestDto addInterest(Long userId, String skill) {
        if (skill == null || skill.isBlank()) throw new IllegalArgumentException("Choose a skill to add.");
        String cleaned = skill.replaceAll("\\s+", " ").trim();
        if (cleaned.length() > 150) throw new IllegalArgumentException("The skill name is too long.");
        String key = MatchingEngine.normalizeSkill(cleaned);
        if (key.isEmpty()) throw new IllegalArgumentException("Choose a skill to add.");

        JobSeekerProfile seeker = requireProfile(userId);
        SkillLearningInterest interest = interestRepository
            .findBySeekerIdAndSkillKey(seeker.getId(), key)
            .orElseGet(() -> interestRepository.save(SkillLearningInterest.builder()
                .seeker(seeker)
                .skillName(cleaned)
                .skillKey(key)
                .build()));

        LearningInterestDto dto = new LearningInterestDto();
        dto.setId(interest.getId());
        dto.setSkill(interest.getSkillName());
        dto.setSkillKey(interest.getSkillKey());
        dto.setCreatedAt(interest.getCreatedAt());
        return dto;
    }

    @Transactional
    public void removeInterest(Long userId, String skill) {
        JobSeekerProfile seeker = requireProfile(userId);
        String key = MatchingEngine.normalizeSkill(skill);
        if (key.isEmpty()) throw new IllegalArgumentException("A skill is required.");
        interestRepository.deleteBySeekerIdAndSkillKey(seeker.getId(), key);
    }

    /* ================= helpers ================= */

    private JobSeekerProfile requireProfile(Long userId) {
        if (userId == null) throw new IllegalArgumentException("You must be signed in.");
        return seekerRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Complete your job seeker profile first."));
    }

    private double currentScore(Map<Long, Double> cache, JobSeekerProfile seeker, JobPost job) {
        return cache.computeIfAbsent(job.getId(),
            ignored -> matchingEngine.calculateMatch(seeker, job).getOverallMatch());
    }

    private JobSeekerProfile copyWithSkills(JobSeekerProfile source, List<String> extra) {
        List<String> merged = displaySkills(source.getSkills());
        Set<String> seen = new HashSet<>();
        for (String skill : merged) seen.add(MatchingEngine.normalizeSkill(skill));
        for (String skill : extra) {
            String key = MatchingEngine.normalizeSkill(skill);
            if (seen.add(key)) merged.add(skill);
        }
        return JobSeekerProfile.builder()
            .fullName(source.getFullName())
            .contactNumber(source.getContactNumber())
            .municipality(source.getMunicipality())
            .barangay(source.getBarangay())
            .educationLevel(source.getEducationLevel())
            .courseField(source.getCourseField())
            .workExperience(source.getWorkExperience())
            .certifications(source.getCertifications())
            .employmentTypePreference(source.getEmploymentTypePreference())
            .preferredWorkSchedule(source.getPreferredWorkSchedule())
            .availability(source.getAvailability())
            .expectedSalaryMin(source.getExpectedSalaryMin())
            .expectedSalaryMax(source.getExpectedSalaryMax())
            .profilePhoto(source.getProfilePhoto())
            .skills(String.join(", ", merged))
            .build();
    }

    private List<String> displaySkills(String raw) {
        return new ArrayList<>(MatchingEngine.splitSkills(raw));
    }

    private Map<String, String> seekerSpellings() {
        Map<String, Map<String, Integer>> spellings = new HashMap<>();
        for (JobSeekerProfile profile : seekerRepository.findAll()) {
            for (String skill : MatchingEngine.splitSkills(profile.getSkills())) {
                String key = MatchingEngine.normalizeSkill(skill);
                if (key.isEmpty()) continue;
                spellings.computeIfAbsent(key, ignored -> new LinkedHashMap<>())
                    .merge(skill.trim(), 1, Integer::sum);
            }
        }
        Map<String, String> display = new HashMap<>();
        for (Map.Entry<String, Map<String, Integer>> entry : spellings.entrySet()) {
            entry.getValue().entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(best -> display.put(entry.getKey(), best.getKey()));
        }
        return display;
    }
}
