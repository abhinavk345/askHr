package com.intech.ai.controller;

import com.intech.ai.modal.LoginRequest;
import com.intech.ai.service.EmployeeService;
import com.intech.ai.service.JwtService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/askhr/api/v1")
public class AuthController {

    private final JwtService jwtService;
    private final EmployeeService employeeService;

    public AuthController(JwtService jwtService, EmployeeService employeeService) {
        this.jwtService = jwtService;
        this.employeeService = employeeService;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        boolean valid = employeeService.validateCredentials(
                request.getEmployeeId(),
                request.getPassword()
        );

        if (!valid) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("message", "Invalid credentials"));
        }

        String token = jwtService.generateToken(request.getEmployeeId(), "ROLE_USER");

        return ResponseEntity.ok(Map.of(
                "message", "Login successful",
                "employeeId", request.getEmployeeId(),
                "token", token,
                "role", "ROLE_USER"
        ));
    }
}
