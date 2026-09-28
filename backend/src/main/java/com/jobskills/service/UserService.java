package com.jobskills.service;

import com.jobskills.dto.AuthResponse;
import com.jobskills.dto.RegisterRequest;
import com.jobskills.model.*;
import com.jobskills.model.enums.*;
import com.jobskills.repository.*;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.math.BigDecimal;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    private static final String UPLOAD_DIR = "uploads/";

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .userType(UserType.valueOf(request.getUserType().toUpperCase()))
            .build();
        user = userRepository.save(user);

        if (request.getUserType().equalsIgnoreCase("job_seeker")) {
            JobSeekerProfile profile = JobSeekerProfile.builder()
                .user(user)
                .fullName(request.getFullName())
                .contactNumber(request.getContactNumber())
                .municipality(request.getMunicipality())
                .barangay(request.getBarangay())
                .educationLevel(request.getEducationLevel())
                .courseField(request.getCourseField())
                .skills(request.getSkills())
                .workExperience(request.getWorkExperience())
                .certifications(request.getCertifications())
                .employmentTypePreference(request.getEmploymentTypePreference())
                .preferredWorkSchedule(request.getPreferredWorkSchedule())
                .availability(request.getAvailability())
                .expectedSalaryMin(request.getExpectedSalary() != null ? BigDecimal.valueOf(request.getExpectedSalary()) : null)
                .expectedSalaryMax(request.getExpectedSalary() != null ? BigDecimal.valueOf(request.getExpectedSalary()) : null)
                .build();
            seekerRepository.save(profile);
            return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                "JOB_SEEKER",
                request.getFullName(),
                false,
                user.getId()
            );
        } else {
            EmployerProfile profile = EmployerProfile.builder()
                .user(user)
                .businessName(request.getBusinessName())
                .employerName(request.getEmployerName())
                .contactNumber(request.getContactNumber())
                .businessType(BusinessType.valueOf(request.getBusinessType().toUpperCase()))
                .municipality(request.getMunicipality())
                .barangay(request.getBarangay())
                .businessAddress(request.getBusinessAddress())
                .businessDescription(request.getBusinessDescription())
                .build();
            employerRepository.save(profile);
            return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                "EMPLOYER",
                request.getBusinessName(),
                false,
                user.getId()
            );
        }
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw new IllegalArgumentException("Invalid credentials");
        }

        String name = user.getUserType() == UserType.JOB_SEEKER
            ? seekerRepository.findByUserId(user.getId()).map(JobSeekerProfile::getFullName).orElse(email)
            : employerRepository.findByUserId(user.getId()).map(EmployerProfile::getBusinessName).orElse(email);

        return new AuthResponse(
            jwtService.generateToken(user),
            jwtService.generateRefreshToken(user),
            user.getUserType().name(),
            name,
            user.isVerified(),
            user.getId()
        );
    }

    public JobSeekerProfile getJobSeekerProfile(Long userId) {
        return seekerRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
    }

    public EmployerProfile getEmployerProfile(Long userId) {
        return employerRepository.findByUserId(userId)
            .orElseThrow(() -> new IllegalArgumentException("Profile not found"));
    }

    @Transactional
    public JobSeekerProfile updateJobSeekerProfile(Long userId, RegisterRequest request, MultipartFile photo) {
        JobSeekerProfile profile = getJobSeekerProfile(userId);

        if (request.getFullName() != null) profile.setFullName(request.getFullName());
        if (request.getContactNumber() != null) profile.setContactNumber(request.getContactNumber());
        if (request.getMunicipality() != null) profile.setMunicipality(request.getMunicipality());
        if (request.getBarangay() != null) profile.setBarangay(request.getBarangay());
        if (request.getEducationLevel() != null) profile.setEducationLevel(request.getEducationLevel());
        if (request.getCourseField() != null) profile.setCourseField(request.getCourseField());
        if (request.getSkills() != null) profile.setSkills(request.getSkills());
        if (request.getWorkExperience() != null) profile.setWorkExperience(request.getWorkExperience());
        if (request.getCertifications() != null) profile.setCertifications(request.getCertifications());
        if (request.getEmploymentTypePreference() != null) profile.setEmploymentTypePreference(request.getEmploymentTypePreference());
        if (request.getPreferredWorkSchedule() != null) profile.setPreferredWorkSchedule(request.getPreferredWorkSchedule());
        if (request.getAvailability() != null) profile.setAvailability(request.getAvailability());
        if (request.getExpectedSalary() != null) {
            BigDecimal salary = BigDecimal.valueOf(request.getExpectedSalary());
            profile.setExpectedSalaryMin(salary);
            profile.setExpectedSalaryMax(salary);
        }

        if (photo != null && !photo.isEmpty()) {
            String filename = saveFile(photo);
            profile.setProfilePhoto(filename);
        }

        return seekerRepository.save(profile);
    }

    @Transactional
    public EmployerProfile updateEmployerProfile(Long userId, RegisterRequest request, MultipartFile logo) {
        EmployerProfile profile = getEmployerProfile(userId);

        if (request.getBusinessName() != null) profile.setBusinessName(request.getBusinessName());
        if (request.getEmployerName() != null) profile.setEmployerName(request.getEmployerName());
        if (request.getContactNumber() != null) profile.setContactNumber(request.getContactNumber());
        if (request.getBusinessType() != null) profile.setBusinessType(BusinessType.valueOf(request.getBusinessType().toUpperCase()));
        if (request.getMunicipality() != null) profile.setMunicipality(request.getMunicipality());
        if (request.getBarangay() != null) profile.setBarangay(request.getBarangay());
        if (request.getBusinessAddress() != null) profile.setBusinessAddress(request.getBusinessAddress());
        if (request.getBusinessDescription() != null) profile.setBusinessDescription(request.getBusinessDescription());

        if (logo != null && !logo.isEmpty()) {
            String filename = saveFile(logo);
            profile.setLogo(filename);
        }

        return employerRepository.save(profile);
    }

    public int calculateProfileCompletion(JobSeekerProfile profile) {
        int completed = 0;
        int total = 12;

        if (profile.getFullName() != null && !profile.getFullName().isEmpty()) completed++;
        if (profile.getContactNumber() != null && !profile.getContactNumber().isEmpty()) completed++;
        if (profile.getMunicipality() != null) completed++;
        if (profile.getBarangay() != null && !profile.getBarangay().isEmpty()) completed++;
        if (profile.getEducationLevel() != null) completed++;
        if (profile.getCourseField() != null && !profile.getCourseField().isEmpty()) completed++;
        if (profile.getSkills() != null && !profile.getSkills().isEmpty()) completed++;
        if (profile.getWorkExperience() != null && !profile.getWorkExperience().isEmpty()) completed++;
        if (profile.getCertifications() != null && !profile.getCertifications().isEmpty()) completed++;
        if (profile.getEmploymentTypePreference() != null) completed++;
        if (profile.getAvailability() != null) completed++;
        if (profile.getExpectedSalaryMin() != null) completed++;

        return (completed * 100) / total;
    }

    private String saveFile(MultipartFile file) {
        try {
            Files.createDirectories(Paths.get(UPLOAD_DIR));
            String filename = UUID.randomUUID() + "_" + file.getOriginalFilename();
            Path path = Paths.get(UPLOAD_DIR + filename);
            Files.write(path, file.getBytes());
            return filename;
        } catch (IOException e) {
            throw new RuntimeException("Failed to save file", e);
        }
    }
}