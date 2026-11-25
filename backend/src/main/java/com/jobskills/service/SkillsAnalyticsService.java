package com.jobskills.service;

import com.jobskills.dto.LearningResourceRequest;
import com.jobskills.dto.SkillDemandDto;
import com.jobskills.dto.SkillsAnalyticsDto;
import com.jobskills.model.JobPost;
import com.jobskills.model.JobSeekerProfile;
import com.jobskills.model.LearningResource;
import com.jobskills.model.enums.EmploymentType;
import com.jobskills.model.enums.JobCategory;
import com.jobskills.model.enums.Municipality;
import com.jobskills.repository.JobSeekerProfileRepository;
import com.jobskills.repository.LearningResourceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

/**
 * Skills demand and supply analytics. Demand is always the number of real
 * active postings requiring a skill; supply is the number of profiles that
 * record it. No estimate, sample or projection is produced.
 */
@Service
@RequiredArgsConstructor
public class SkillsAnalyticsService {

    private final SkillsDemandService demandService;
    private final JobSeekerProfileRepository seekerRepository;
    private final LearningResourceRepository resourceRepository;

    @Transactional(readOnly = true)
    public SkillsAnalyticsDto demand(Municipality municipality, String barangay,
                                     EmploymentType employmentType, JobCategory category, Integer days) {
        List<JobPost> all = demandService.activeJobs();
        List<JobPost> jobs = demandService.filter(all, municipality, barangay, employmentType, category, days);
        Map<String, Integer> supply = demandService.seekerSupply();

        SkillsAnalyticsDto dto = new SkillsAnalyticsDto();
        Map<String, Object> totals = new LinkedHashMap<>();
        totals.put("activeJobs", jobs.size());
        totals.put("jobsWithSkills", demandService.jobsWithSkills(jobs));
        totals.put("jobsWithoutSkills", jobs.size() - demandService.jobsWithSkills(jobs));
        totals.put("uniqueSkills", demandService.bySkill(jobs).size());
        dto.setTotals(totals);

        Map<String, Object> filters = new LinkedHashMap<>();
        filters.put("municipality", municipality);
        filters.put("municipalityLabel", municipality == null ? null
            : SkillsDemandService.municipalityLabel(municipality));
        filters.put("barangay", barangay == null || barangay.isBlank() ? null : barangay.trim());
        filters.put("employmentType", employmentType);
        filters.put("employmentTypeLabel", employmentType == null ? null
            : SkillsDemandService.typeLabel(employmentType));
        filters.put("category", category);
        filters.put("categoryLabel", category == null ? null : SkillsDemandService.categoryLabel(category));
        filters.put("days", days);
        dto.setFilters(filters);

        dto.setMostRequired(demandService.summarize(jobs, supply, 20));

        Map<String, List<SkillDemandDto>> byMunicipality = new LinkedHashMap<>();
        for (Municipality value : Municipality.values()) {
            List<JobPost> inMunicipality = demandService.filter(jobs, value, null, null, null, null);
            byMunicipality.put(value.name(), demandService.summarize(inMunicipality, supply, 8));
        }
        dto.setByMunicipality(byMunicipality);

        Map<String, List<SkillDemandDto>> byType = new LinkedHashMap<>();
        for (EmploymentType value : EmploymentType.values()) {
            List<JobPost> inType = demandService.filter(jobs, null, null, value, null, null);
            byType.put(value.name(), demandService.summarize(inType, supply, 8));
        }
        dto.setByEmploymentType(byType);

        dto.setTrendAvailable(false);
        dto.setTrendNote("Not calculated: JSM stores each posting's current requirements, not historical "
            + "snapshots, so these figures are 'Most Frequently Required', not growth or decline.");
        return dto;
    }

    @Transactional(readOnly = true)
    public SkillsAnalyticsDto analytics(Municipality municipality, EmploymentType employmentType,
                                        JobCategory category, Integer days) {
        SkillsAnalyticsDto dto = demand(municipality, null, employmentType, category, days);
        List<JobPost> jobs = demandService.filter(demandService.activeJobs(), municipality, null,
            employmentType, category, days);
        List<JobSeekerProfile> seekers = seekerRepository.findAll();
        Map<String, Integer> supply = demandService.seekerSupply();
        Map<String, List<JobPost>> bySkill = demandService.bySkill(jobs);
        Map<String, String> seekerSpellings = new HashMap<>();
        for (JobSeekerProfile profile : seekers) {
            for (String skill : MatchingEngine.splitSkills(profile.getSkills())) {
                String key = MatchingEngine.normalizeSkill(skill);
                if (!key.isEmpty()) seekerSpellings.putIfAbsent(key, skill.trim());
            }
        }

        int seekersWithSkills = 0;
        int recordedSkills = 0;
        for (JobSeekerProfile profile : seekers) {
            int count = MatchingEngine.splitSkills(profile.getSkills()).size();
            if (count > 0) seekersWithSkills++;
            recordedSkills += count;
        }

        Map<String, Object> totals = new LinkedHashMap<>(dto.getTotals());
        totals.put("totalSeekers", seekers.size());
        totals.put("seekersWithSkills", seekersWithSkills);
        totals.put("skillsRecordedBySeekers", recordedSkills);
        totals.put("totalUniqueSkills", bySkill.size());
        totals.put("trendAvailable", false);
        dto.setTotals(totals);

        Set<String> keys = new LinkedHashSet<>(bySkill.keySet());
        keys.addAll(supply.keySet());
        List<SkillsAnalyticsDto.SupplyDemand> supplyDemand = new ArrayList<>();
        for (String key : keys) {
            List<JobPost> requiring = bySkill.getOrDefault(key, List.of());
            SkillsAnalyticsDto.SupplyDemand row = new SkillsAnalyticsDto.SupplyDemand();
            row.setSkillKey(key);
            row.setSkill(requiring.isEmpty()
                ? seekerSpellings.getOrDefault(key, key)
                : demandService.displayName(key, requiring));
            row.setDemandPostings(requiring.size());
            row.setSupplySeekers(supply.getOrDefault(key, 0));
            row.setGap(row.getDemandPostings() - row.getSupplySeekers());
            row.setImbalance(row.getGap() > 0 ? "SHORTAGE" : row.getGap() < 0 ? "SURPLUS" : "BALANCED");
            int positions = 0;
            for (JobPost job : requiring) positions += Math.max(0, job.getAvailablePositions());
            row.setPositionsOffered(positions);
            supplyDemand.add(row);
        }
        supplyDemand.sort(Comparator.comparingInt(SkillsAnalyticsDto.SupplyDemand::getDemandPostings).reversed()
            .thenComparing(Comparator.comparingInt(SkillsAnalyticsDto.SupplyDemand::getGap).reversed())
            .thenComparing(SkillsAnalyticsDto.SupplyDemand::getSkill, String.CASE_INSENSITIVE_ORDER));
        if (supplyDemand.size() > 50) supplyDemand = new ArrayList<>(supplyDemand.subList(0, 50));
        dto.setSupplyDemand(supplyDemand);

        Map<String, String> methodology = new LinkedHashMap<>();
        methodology.put("demand", "Demand = number of active job postings in the filtered set whose required "
            + "skills include the skill. It counts postings, not vacancies; available positions are shown separately.");
        methodology.put("supply", "Supply = number of job seeker profiles that record the skill, counted once "
            + "per profile. Skills are normalized by case and spacing, so 'Customer Service' and "
            + "'customer service' are the same skill.");
        methodology.put("gap", "Gap = demand postings - supply profiles. Positive means fewer recorded seekers "
            + "than postings requiring the skill. It is a planning signal, not a count of unfilled jobs.");
        methodology.put("percent", "Percentage = postings requiring the skill / postings that list any skill. "
            + "Postings without required skills are excluded from that denominator and reported separately.");
        methodology.put("trend", "No trend is calculated because no historical demand snapshots are stored. "
            + "Results are labelled 'Most Frequently Required'.");
        dto.setMethodology(methodology);
        return dto;
    }

    /* ================= learning resources ================= */

    @Transactional(readOnly = true)
    public List<LearningResource> allResources() {
        return resourceRepository.findAllByOrderByUpdatedAtDescCreatedAtDesc();
    }

    @Transactional
    public LearningResource createResource(LearningResourceRequest request) {
        LearningResource resource = new LearningResource();
        apply(resource, request, true);
        return resourceRepository.save(resource);
    }

    @Transactional
    public LearningResource updateResource(Long id, LearningResourceRequest request) {
        LearningResource resource = resourceRepository.findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Learning resource not found."));
        apply(resource, request, false);
        return resourceRepository.save(resource);
    }

    @Transactional
    public void deleteResource(Long id) {
        if (!resourceRepository.existsById(id)) {
            throw new IllegalArgumentException("Learning resource not found.");
        }
        resourceRepository.deleteById(id);
    }

    private void apply(LearningResource resource, LearningResourceRequest request, boolean creating) {
        if (request == null) throw new IllegalArgumentException("A learning resource is required.");

        String skill = trimmed(request.getSkillName());
        if (skill.isEmpty()) throw new IllegalArgumentException("The skill taught is required.");
        if (skill.length() > 150) throw new IllegalArgumentException("The skill name is too long.");
        String title = trimmed(request.getTitle());
        if (title.isEmpty()) throw new IllegalArgumentException("The course title is required.");
        if (title.length() > 250) throw new IllegalArgumentException("The course title is too long.");
        String provider = trimmed(request.getProvider());
        if (provider.isEmpty()) throw new IllegalArgumentException("The provider is required.");
        if (provider.length() > 200) throw new IllegalArgumentException("The provider name is too long.");
        String url = trimmed(request.getUrl());
        if (!url.matches("(?i)^https?://\\S+$")) {
            throw new IllegalArgumentException("Enter a real http(s) resource URL.");
        }
        if (url.length() > 500) throw new IllegalArgumentException("The URL is too long.");
        if (request.getCost() != null && request.getCost().compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Cost cannot be negative.");
        }

        resource.setSkillName(skill);
        resource.setSkillKey(MatchingEngine.normalizeSkill(skill));
        resource.setTitle(title);
        resource.setProvider(provider);
        resource.setUrl(url);
        resource.setCost(request.getCost());
        resource.setFree(Boolean.TRUE.equals(request.getFree()));
        resource.setOnline(request.getOnline() == null || request.getOnline());
        resource.setDuration(trimmedOrNull(request.getDuration()));
        resource.setDescription(trimmedOrNull(request.getDescription()));
        resource.setAvailability(trimmedOrNull(request.getAvailability()));
        if (request.getVerifiedAt() != null) {
            resource.setVerifiedAt(request.getVerifiedAt());
        } else if (creating) {
            resource.setVerifiedAt(LocalDate.now());
        }
        resource.setActive(request.getActive() == null ? creating || resource.isActive()
            : request.getActive());
    }

    private String trimmed(String raw) {
        return raw == null ? "" : raw.replaceAll("\\s+", " ").trim();
    }

    private String trimmedOrNull(String raw) {
        String value = trimmed(raw);
        return value.isEmpty() ? null : value;
    }
}
