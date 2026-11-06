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
import java.math.BigDecimal;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final JobSeekerProfileRepository seekerRepository;
    private final EmployerProfileRepository employerRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final UploadedFileRepository uploadedFileRepository;

    /** Labels the registration forms send, mapped to the business type enum. */
    private static final Map<String, BusinessType> BUSINESS_TYPE_LABELS = Map.ofEntries(
        Map.entry("retail/store", BusinessType.RETAIL_STORE),
        Map.entry("restaurant/food service", BusinessType.RESTAURANT_FOOD_SERVICE),
        Map.entry("hospitality/tourism", BusinessType.HOSPITALITY),
        Map.entry("office/administrative", BusinessType.OFFICE_ADMINISTRATION),
        Map.entry("healthcare/medical", BusinessType.HEALTHCARE),
        Map.entry("education/training", BusinessType.EDUCATION),
        Map.entry("construction/engineering", BusinessType.CONSTRUCTION),
        Map.entry("agriculture/farming", BusinessType.AGRICULTURE),
        Map.entry("fisheries/aquaculture", BusinessType.FISHERIES),
        Map.entry("manufacturing/production", BusinessType.MANUFACTURING),
        Map.entry("logistics/transportation", BusinessType.LOGISTICS_DELIVERY),
        Map.entry("automotive/repair", BusinessType.AUTOMOTIVE),
        Map.entry("security/services", BusinessType.SECURITY),
        Map.entry("it/technology", BusinessType.IT_TECHNOLOGY),
        Map.entry("sales/marketing", BusinessType.OTHER),
        Map.entry("other", BusinessType.OTHER)
    );

    @Transactional
    public AuthResponse register(RegisterRequest request, MultipartFile profilePhoto, MultipartFile logo) {
        String email = normalizeEmail(request.getEmail());
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Email already registered");
        }

        UserType userType = parseUserType(request.getUserType());

        User user = User.builder()
            .email(email)
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .userType(userType)
            .build();
        user = userRepository.save(user);

        if (userType == UserType.JOB_SEEKER) {
            JobSeekerProfile profile = JobSeekerProfile.builder()
                .user(user)
                .fullName(requireText(request.getFullName(), "fullName"))
                .contactNumber(request.getContactNumber())
                .municipality(parseMunicipality(request.getMunicipality(), true))
                .barangay(request.getBarangay())
                .educationLevel(parseEducationLevel(request.getEducationLevel()))
                .courseField(request.getCourseField())
                .skills(request.getSkills())
                .workExperience(request.getWorkExperience())
                .certifications(request.getCertifications())
                .employmentTypePreference(parseEmploymentType(request.getEmploymentTypePreference()))
                .preferredWorkSchedule(parseWorkSchedule(request.getPreferredWorkSchedule()))
                .availability(parseAvailability(request.getAvailability()))
                .expectedSalaryMin(toAmount(request.getExpectedSalaryMin()))
                .expectedSalaryMax(toAmount(request.getExpectedSalaryMax()))
                .build();

            if (profilePhoto != null && !profilePhoto.isEmpty()) {
                profile.setProfilePhoto(saveFile(profilePhoto));
            }

            seekerRepository.save(profile);
            return new AuthResponse(
                jwtService.generateToken(user),
                jwtService.generateRefreshToken(user),
                "JOB_SEEKER",
                profile.getFullName(),
                false,
                user.getId()
            );
        }

        EmployerProfile profile = EmployerProfile.builder()
            .user(user)
            .businessName(requireText(request.getBusinessName(), "businessName"))
            .employerName(requireText(request.getEmployerName(), "employerName"))
            .contactNumber(request.getContactNumber())
            .businessType(parseBusinessType(request.getBusinessType()))
            .municipality(parseMunicipality(request.getMunicipality(), true))
            .barangay(request.getBarangay())
            .businessAddress(request.getBusinessAddress())
            .businessDescription(request.getBusinessDescription())
            .build();

        if (logo != null && !logo.isEmpty()) {
            profile.setLogo(saveFile(logo));
        }

        employerRepository.save(profile);
        return new AuthResponse(
            jwtService.generateToken(user),
            jwtService.generateRefreshToken(user),
            "EMPLOYER",
            profile.getBusinessName(),
            false,
            user.getId()
        );
    }

    @Transactional
    public AuthResponse login(String email, String password) {
        String normalizedEmail = normalizeEmail(email);

        // Check if this is an admin login with name only (no password)
        // Accept either "admin" or "admin++" as the admin name
        // Hash: render-force-redeploy-20261106
        boolean isAdminNameLogin = (normalizedEmail.equalsIgnoreCase("admin") || normalizedEmail.equalsIgnoreCase("admin++")) && (password == null || password.trim().isEmpty());

        User user;
        if (isAdminNameLogin) {
            // Admin login: find user by email (which is the admin name)
            // and allow access without password check
            user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid admin credentials"));

            if (!user.isActive()) {
                throw new IllegalArgumentException("This admin account has been deactivated");
            }
        } else {
            // Normal login: user + password required
            user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new IllegalArgumentException("Invalid credentials"));

            if (!passwordEncoder.matches(password, user.getPasswordHash())) {
                throw new IllegalArgumentException("Invalid credentials");
            }

            if (!user.isActive()) {
                throw new IllegalArgumentException("This account has been deactivated");
            }
        }

        String name = user.getUserType() == UserType.JOB_SEEKER
            ? seekerRepository.findByUserId(user.getId()).map(JobSeekerProfile::getFullName).orElse(user.getEmail())
            : employerRepository.findByUserId(user.getId()).map(EmployerProfile::getBusinessName).orElse(user.getEmail());

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
        if (request.getMunicipality() != null) profile.setMunicipality(parseMunicipality(request.getMunicipality(), true));
        if (request.getBarangay() != null) profile.setBarangay(request.getBarangay());
        if (request.getEducationLevel() != null) profile.setEducationLevel(parseEducationLevel(request.getEducationLevel()));
        if (request.getCourseField() != null) profile.setCourseField(request.getCourseField());
        if (request.getSkills() != null) profile.setSkills(request.getSkills());
        if (request.getWorkExperience() != null) profile.setWorkExperience(request.getWorkExperience());
        if (request.getCertifications() != null) profile.setCertifications(request.getCertifications());
        if (request.getEmploymentTypePreference() != null) {
            profile.setEmploymentTypePreference(parseEmploymentType(request.getEmploymentTypePreference()));
        }
        if (request.getPreferredWorkSchedule() != null) {
            profile.setPreferredWorkSchedule(parseWorkSchedule(request.getPreferredWorkSchedule()));
        }
        if (request.getAvailability() != null) profile.setAvailability(parseAvailability(request.getAvailability()));
        if (request.getExpectedSalaryMin() != null) {
            profile.setExpectedSalaryMin(toAmount(request.getExpectedSalaryMin()));
        }
        if (request.getExpectedSalaryMax() != null) {
            profile.setExpectedSalaryMax(toAmount(request.getExpectedSalaryMax()));
        }

        if (photo != null && !photo.isEmpty()) {
            profile.setProfilePhoto(saveFile(photo));
        }

        return seekerRepository.save(profile);
    }

    @Transactional
    public EmployerProfile updateEmployerProfile(Long userId, RegisterRequest request, MultipartFile logo) {
        EmployerProfile profile = getEmployerProfile(userId);

        if (request.getBusinessName() != null) profile.setBusinessName(request.getBusinessName());
        if (request.getEmployerName() != null) profile.setEmployerName(request.getEmployerName());
        if (request.getContactNumber() != null) profile.setContactNumber(request.getContactNumber());
        if (request.getBusinessType() != null) profile.setBusinessType(parseBusinessType(request.getBusinessType()));
        if (request.getMunicipality() != null) profile.setMunicipality(parseMunicipality(request.getMunicipality(), true));
        if (request.getBarangay() != null) profile.setBarangay(request.getBarangay());
        if (request.getBusinessAddress() != null) profile.setBusinessAddress(request.getBusinessAddress());
        if (request.getBusinessDescription() != null) profile.setBusinessDescription(request.getBusinessDescription());

        if (logo != null && !logo.isEmpty()) {
            profile.setLogo(saveFile(logo));
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

    // ---- input parsing -----------------------------------------------------
    // Every value the client sends is checked once, here. Bad input produces a
    // clear 400 message instead of a null column that breaks another screen later.

    private UserType parseUserType(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("userType is required: JOB_SEEKER or EMPLOYER");
        }
        UserType type;
        try {
            type = UserType.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid userType: " + raw + ". Must be JOB_SEEKER or EMPLOYER.");
        }
        // Registration may never create an admin. Admins are seeded or created
        // by an existing admin, never by the public endpoint.
        if (type != UserType.JOB_SEEKER && type != UserType.EMPLOYER) {
            throw new IllegalArgumentException("Self-registration is only allowed as JOB_SEEKER or EMPLOYER.");
        }
        return type;
    }

    private Municipality parseMunicipality(String raw, boolean required) {
        if (raw == null || raw.isBlank()) {
            if (required) {
                throw new IllegalArgumentException("municipality is required: Basud, Daet, Vinzons or Labo");
            }
            return null;
        }
        try {
            return Municipality.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown municipality: " + raw + ". Use Basud, Daet, Vinzons or Labo.");
        }
    }

    private EducationLevel parseEducationLevel(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return EducationLevel.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown educationLevel: " + raw
                + ". Use No Formal Education, Elementary Graduate, High School Graduate,"
                + " Vocational/Technical, College Graduate or Post Graduate.");
        }
    }

    private EmploymentType parseEmploymentType(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return EmploymentType.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown employmentType: " + raw);
        }
    }

    private WorkSchedule parseWorkSchedule(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return WorkSchedule.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown work schedule: " + raw);
        }
    }

    private Availability parseAvailability(String raw) {
        if (raw == null || raw.isBlank()) return null;
        try {
            return Availability.valueOf(normalizeEnum(raw));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown availability: " + raw);
        }
    }

    private BusinessType parseBusinessType(String raw) {
        String value = requireText(raw, "businessType");
        BusinessType mapped = BUSINESS_TYPE_LABELS.get(value.toLowerCase().replace(" / ", "/"));
        if (mapped != null) return mapped;
        try {
            return BusinessType.valueOf(normalizeEnum(value));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unknown businessType: " + raw);
        }
    }

    private String normalizeEnum(String raw) {
        return raw.trim().toUpperCase().replace(" ", "_").replace("-", "_").replace("/", "_");
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("email is required");
        }
        return email.trim().toLowerCase();
    }

    private String requireText(String raw, String field) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return raw.trim();
    }

    private BigDecimal toAmount(Double amount) {
        return amount == null ? null : BigDecimal.valueOf(amount);
    }

    private String saveFile(MultipartFile file) {
        try {
            String original = file.getOriginalFilename() == null ? "upload" : file.getOriginalFilename();
            // Keep only the file name, never any directory part the client sent.
            String base = Paths.get(original).getFileName().toString().replaceAll("[^A-Za-z0-9._-]", "_");
            String filename = UUID.randomUUID() + "_" + base;
            // Stored in PostgreSQL, not on disk: the container filesystem is
            // wiped on every restart and spin-down on managed hosts.
            uploadedFileRepository.save(UploadedFile.builder()
                .filename(filename)
                .contentType(file.getContentType())
                .sizeBytes(file.getSize() > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) file.getSize())
                .data(file.getBytes())
                .build());
            return filename;
        } catch (IOException e) {
            throw new IllegalStateException("Failed to save file", e);
        }
    }
}
