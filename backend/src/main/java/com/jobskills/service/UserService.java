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
    public AuthResponse register(RegisterRequest request, MultipartFile profilePhoto, MultipartFile logo) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already registered");
        }

        // Convert string values to appropriate enum types
        UserType userType;
        try {
            userType = UserType.valueOf(request.getUserType().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid user type: " + request.getUserType());
        }

        User user = User.builder()
            .email(request.getEmail())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .userType(userType)
            .build();
        user = userRepository.save(user);

        if (request.getUserType().equalsIgnoreCase("job_seeker")) {
            // Convert string values to enum types
            Municipality municipality = null;
            try { municipality = Municipality.valueOf(request.getMunicipality().toUpperCase()); } catch (Exception e) {}

            EducationLevel educationLevel = null;
            try { educationLevel = mapEducationLevel(request.getEducationLevel()); } catch (Exception e) {}

            EmploymentType employmentType = null;
            try { employmentType = mapEmploymentType(request.getEmploymentTypePreference()); } catch (Exception e) {}

            WorkSchedule workSchedule = null;
            try { workSchedule = mapWorkSchedule(request.getPreferredWorkSchedule()); } catch (Exception e) {}

            Availability availability = null;
            try { availability = mapAvailability(request.getAvailability()); } catch (Exception e) {}

            JobSeekerProfile profile = JobSeekerProfile.builder()
                .user(user)
                .fullName(request.getFullName())
                .contactNumber(request.getContactNumber())
                .municipality(municipality)
                .barangay(request.getBarangay())
                .educationLevel(educationLevel)
                .courseField(request.getCourseField())
                .skills(request.getSkills())
                .workExperience(request.getWorkExperience())
                .certifications(request.getCertifications())
                .employmentTypePreference(employmentType)
                .preferredWorkSchedule(workSchedule)
                .availability(availability)
                .expectedSalaryMin(request.getExpectedSalaryMin() != null ? BigDecimal.valueOf(request.getExpectedSalaryMin()) : null)
                .expectedSalaryMax(request.getExpectedSalaryMax() != null ? BigDecimal.valueOf(request.getExpectedSalaryMax()) : null)
                .build();

            if (profilePhoto != null && !profilePhoto.isEmpty()) {
                String filename = saveFile(profilePhoto);
                profile.setProfilePhoto(filename);
            }

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
            // Convert string values to enum types
            Municipality municipality = null;
            try { municipality = Municipality.valueOf(request.getMunicipality().toUpperCase()); } catch (Exception e) {}

            BusinessType businessType = null;
            try {
                String businessTypeStr = mapBusinessType(request.getBusinessType());
                businessType = BusinessType.valueOf(businessTypeStr);
            } catch (Exception e) {}

            EmployerProfile profile = EmployerProfile.builder()
                .user(user)
                .businessName(request.getBusinessName())
                .employerName(request.getEmployerName())
                .contactNumber(request.getContactNumber())
                .businessType(businessType)
                .municipality(municipality)
                .barangay(request.getBarangay())
                .businessAddress(request.getBusinessAddress())
                .businessDescription(request.getBusinessDescription())
                .build();

            if (logo != null && !logo.isEmpty()) {
                String filename = saveFile(logo);
                profile.setLogo(filename);
            }

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
        if (request.getMunicipality() != null) profile.setMunicipality(Municipality.valueOf(request.getMunicipality().toUpperCase()));
        if (request.getBarangay() != null) profile.setBarangay(request.getBarangay());
        if (request.getEducationLevel() != null) profile.setEducationLevel(mapEducationLevel(request.getEducationLevel()));
        if (request.getCourseField() != null) profile.setCourseField(request.getCourseField());
        if (request.getSkills() != null) profile.setSkills(request.getSkills());
        if (request.getWorkExperience() != null) profile.setWorkExperience(request.getWorkExperience());
        if (request.getCertifications() != null) profile.setCertifications(request.getCertifications());
        if (request.getEmploymentTypePreference() != null) profile.setEmploymentTypePreference(mapEmploymentType(request.getEmploymentTypePreference()));
        if (request.getPreferredWorkSchedule() != null) profile.setPreferredWorkSchedule(mapWorkSchedule(request.getPreferredWorkSchedule()));
        if (request.getAvailability() != null) profile.setAvailability(mapAvailability(request.getAvailability()));
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
        if (request.getBusinessType() != null) profile.setBusinessType(BusinessType.valueOf(mapBusinessType(request.getBusinessType())));
        if (request.getMunicipality() != null) profile.setMunicipality(Municipality.valueOf(request.getMunicipality().toUpperCase()));
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

    private EducationLevel mapEducationLevel(String level) {
        if (level == null) return null;
        switch (level.toLowerCase()) {
            case "no formal education": return EducationLevel.NO_FORMAL_EDUCATION;
            case "elementary": return EducationLevel.ELEMENTARY_GRADUATE;
            case "high school": return EducationLevel.HIGH_SCHOOL_GRADUATE;
            case "vocational": return EducationLevel.VOCATIONAL_TECHNICAL;
            case "college": return EducationLevel.COLLEGE_GRADUATE;
            case "post graduate": return EducationLevel.POST_GRADUATE;
            default: return null;
        }
    }

    private String mapBusinessType(String businessType) {
        if (businessType == null) return null;
        // Map frontend business type values to backend enum values
        String lower = businessType.toLowerCase();
        switch (lower) {
            case "retail/store": return "RETAIL_STORE";
            case "restaurant/food service": return "RESTAURANT_FOOD_SERVICE";
            case "hospitality/tourism": return "HOSPITALITY";
            case "agriculture/farming": return "AGRICULTURE";
            case "fisheries/aquaculture": return "FISHERIES";
            case "construction/engineering": return "CONSTRUCTION";
            case "healthcare/medical": return "HEALTHCARE";
            case "education/training": return "EDUCATION";
            case "office/administrative": return "OFFICE_ADMINISTRATION";
            case "sales/marketing": return "IT_TECHNOLOGY"; // Closest match - no sales/marketing in enum
            case "logistics/transportation": return "LOGISTICS_DELIVERY"; // Closest match
            case "manufacturing/production": return "MANUFACTURING";
            case "automotive/repair": return "AUTOMOTIVE";
            case "security/services": return "SECURITY";
            case "it/technology": return "IT_TECHNOLOGY";
            case "other": return "OTHER";
            default: return businessType.toUpperCase().replace(" / ", "_").replace(" ", "_").replace("/", "_");
        }
    }

    private EmploymentType mapEmploymentType(String employmentType) {
        if (employmentType == null) return null;
        // Convert frontend employment type values to backend enum format
        return EmploymentType.valueOf(employmentType.toUpperCase().replace("-", "_"));
    }

    private WorkSchedule mapWorkSchedule(String workSchedule) {
        if (workSchedule == null) return null;
        // Convert frontend work schedule values to backend enum format
        return WorkSchedule.valueOf(workSchedule.toUpperCase());
    }

    private Availability mapAvailability(String availability) {
        if (availability == null) return null;
        // Convert frontend availability values to backend enum format
        return Availability.valueOf(availability.toUpperCase().replace(" ", "_").replace("-", "_"));
    }
}