package com.intech.ai.repository;

import com.intech.ai.modal.EmployeeAuth;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface EmployeeAuthRepository
        extends JpaRepository<EmployeeAuth, Long> {

    Optional<EmployeeAuth> findByUsername(String username);
    Optional<EmployeeAuth> findByEmployeeId(String employeeId);
}