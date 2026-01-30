package com.intech.ai.service;

import com.intech.ai.modal.Employee;
import com.intech.ai.modal.EmployeeAuth;
import com.intech.ai.repository.EmployeeAuthRepository;
import com.intech.ai.repository.EmployeeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployeeRegistrationService {

    private final EmployeeRepository employeeRepo;
    private final EmployeeAuthRepository authRepo;
    private final PasswordEncoder passwordEncoder;

    public EmployeeRegistrationService(
            EmployeeRepository employeeRepo,
            EmployeeAuthRepository authRepo,
            PasswordEncoder passwordEncoder) {
        this.employeeRepo = employeeRepo;
        this.authRepo = authRepo;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public void registerEmployee(
            Employee employee,
            String username,
            String rawPassword,
            String role) {

        // 1️⃣ Save employee profile
        employeeRepo.save(employee);

        // 2️⃣ Create auth record
        EmployeeAuth auth = new EmployeeAuth();
        auth.setEmployeeId(employee.getEmployeeId());
        auth.setUsername(username);
        auth.setPasswordHash(passwordEncoder.encode(rawPassword));
        auth.setRole(role);

        authRepo.save(auth);
    }
}
