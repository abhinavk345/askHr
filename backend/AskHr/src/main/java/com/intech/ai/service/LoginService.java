package com.intech.ai.service;

import com.intech.ai.modal.EmployeeAuth;
import com.intech.ai.repository.EmployeeAuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoginService {

    private static final Logger logger = LoggerFactory.getLogger(LoginService.class);

    private final EmployeeAuthRepository authRepo;
    private final PasswordEncoder passwordEncoder;

    public LoginService(EmployeeAuthRepository authRepo,
                        PasswordEncoder passwordEncoder) {
        this.authRepo = authRepo;
        this.passwordEncoder = passwordEncoder;
    }


    public EmployeeAuth login(String username, String rawPassword) {
        EmployeeAuth auth = authRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        logger.info("auth detail:{} {}", auth.getEmployeeId(),auth.getPasswordHash());
        if (!auth.isEnabled()) {
            throw new RuntimeException("Account disabled");
        }

        boolean matches = passwordEncoder.matches(rawPassword, auth.getPasswordHash());
        logger.info("matched:{}",matches);
        if (!matches) {
            throw new RuntimeException("Invalid credentials");
        }

        auth.setLastLoginAt(LocalDateTime.now());
        authRepo.save(auth);

        logger.info("User {} logged in successfully at {}", username, auth.getLastLoginAt());
        return auth;
    }
}
