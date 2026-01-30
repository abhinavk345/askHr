package com.intech.ai.controller;

import com.intech.ai.modal.Employee;
import com.intech.ai.modal.LoginRequest;
import com.intech.ai.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@CrossOrigin(
        origins = "http://localhost:3001",
        allowCredentials = "true"
)
public class DummyLoginController {

    private final EmployeeService service;

    public DummyLoginController(EmployeeService service) {
        this.service = service;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {

        Employee emp = service.validateLogin(request.getEmployeeId(), request.getPassword());

        if (emp == null) {
            return ResponseEntity
                    .status(401)
                    .body(Map.of("message", "Invalid credentials"));
        }

        return ResponseEntity.ok(
                Map.of(
                        "message", "Login successful",
                        "employeeId", emp.getEmployeeId(),
                        "name", emp.getFullName()
                )
        );
    }

    @GetMapping("/exists")
    public ResponseEntity<?> checkEmployeeExists(
            @RequestParam String employeeId) {

        boolean exists = service.employeeExists(employeeId);

        return ResponseEntity.ok(
                Map.of(
                        "employeeId", employeeId,
                        "exists", exists
                )
        );

    }
}
