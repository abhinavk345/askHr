package com.intech.ai.service;

import com.intech.ai.modal.EmployeeAuth;
import com.intech.ai.repository.EmployeeAuthRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class LoginService {

    private static final Logger logger = LoggerFactory.getLogger(LoginService.class);

    private final EmployeeAuthRepository authRepo;


    public LoginService(EmployeeAuthRepository authRepo ) {
        this.authRepo = authRepo;
    }


    public EmployeeAuth login(String username, String rawPassword) {
        EmployeeAuth auth = authRepo.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("User not found"));
        logger.info("auth detail:{} {}", auth.getEmployeeId(),auth.getPassword());
        if (!auth.isEnabled()) {
            throw new RuntimeException("Account disabled");
        }

        boolean matches = rawPassword.equals(auth.getPassword());
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
