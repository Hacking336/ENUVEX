package com.jobskills.repository;

import com.jobskills.model.EmployerProfile;
import com.jobskills.model.enums.Municipality;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployerProfileRepository extends JpaRepository<EmployerProfile, Long> {
    Optional<EmployerProfile> findByUserId(Long userId);
    Optional<EmployerProfile> findByUser_Email(String email);
    List<EmployerProfile> findByMunicipality(Municipality municipality);
}