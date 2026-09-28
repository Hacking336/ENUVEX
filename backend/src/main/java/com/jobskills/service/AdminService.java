package com.jobskills.service;

import com.jobskills.repository.*;
import com.jobskills.model.enums.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final JobPostRepository jobPostRepository;
    private final JobApplicationRepository applicationRepository;

    public Map<String, Object> getSummaryReport() {
        Map<String, Object> report = new HashMap<>();
        report.put("totalUsers", userRepository.count());
        report.put("totalJobSeekers", userRepository.countByType(UserType.JOB_SEEKER));
        report.put("totalEmployers", userRepository.countByType(UserType.EMPLOYER));
        report.put("activeJobPosts", jobPostRepository.countByIsActiveTrue());
        report.put("totalApplications", applicationRepository.count());
        report.put("hiredApplicants", applicationRepository.countByApplicationStatus(ApplicationStatus.ACCEPTED));

        Map<String, Long> postsByMunicipality = Arrays.stream(Municipality.values())
            .collect(Collectors.toMap(
                Municipality::name,
                m -> (long) jobPostRepository.countByMunicipality(m)
            ));
        report.put("jobPostsByMunicipality", postsByMunicipality);

        return report;
    }
}