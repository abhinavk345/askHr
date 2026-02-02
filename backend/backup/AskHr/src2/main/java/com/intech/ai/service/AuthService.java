package com.intech.ai.service;

import com.intech.ai.modal.Employee;
import com.intech.ai.modal.EmployeeAuth;
import com.intech.ai.repository.EmployeeAuthRepository;
import com.intech.ai.repository.EmployeeRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final EmployeeAuthRepository authRepository;
    private final EmployeeRepository employeeRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(EmployeeAuthRepository authRepository,
                       EmployeeRepository employeeRepository,
                       PasswordEncoder passwordEncoder) {
        this.authRepository = authRepository;
        this.employeeRepository = employeeRepository;
        this.passwordEncoder = passwordEncoder;
    }

    public Employee validateLogin(String username, String rawPassword) {

        EmployeeAuth auth = authRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Invalid credentials"));

        if (!auth.isEnabled()) {
            throw new RuntimeException("Account disabled");
        }

        boolean matches = passwordEncoder.matches(
                rawPassword,
                auth.getPasswordHash()
        );

        if (!matches) {
            throw new RuntimeException("Invalid credentials");
        }

        return employeeRepository.findByEmployeeId(auth.getEmployeeId())
                .orElseThrow(() ->
                        new RuntimeException("Employee profile missing"));
    }
}
