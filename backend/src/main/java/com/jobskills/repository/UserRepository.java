package com.jobskills.repository;

import com.jobskills.model.User;
import com.jobskills.model.enums.UserType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByUserType(UserType userType);
    long countByUserType(UserType userType);
    Optional<User> findFirstByUserType(UserType userType);
}
