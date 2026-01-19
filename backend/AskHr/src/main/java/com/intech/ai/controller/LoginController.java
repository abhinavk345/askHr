package com.intech.ai.controller;

import com.intech.ai.modal.EmployeeAuth;
import com.intech.ai.modal.LoginRequest;
import com.intech.ai.service.LoginService;
import com.intech.ai.utility.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LoginController {

    private static final Logger logger = LoggerFactory.getLogger(LoginController.class);

    private final LoginService loginService;
    private final JwtUtils jwtUtils;

    public LoginController(LoginService loginService, JwtUtils jwtUtils) {
        this.loginService = loginService;
        this.jwtUtils = jwtUtils;
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody LoginRequest request) {
        try {
            EmployeeAuth user = loginService.login(request.getEmployeeId(), request.getPassword());
            String token = jwtUtils.generateToken(user.getEmployeeId(), user.getRole());

            logger.info("Login successful for user {}", user.getEmployeeId());

            return ResponseEntity.ok(Map.of(
                    "message", "Login successful",
                    "employeeId", user.getEmployeeId(),
                    "role", user.getRole(),
                    "token", token
            ));
        } catch (RuntimeException e) {
            logger.warn("Login failed for {}: {}", request.getEmployeeId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", e.getMessage()));
        }
    }
}
