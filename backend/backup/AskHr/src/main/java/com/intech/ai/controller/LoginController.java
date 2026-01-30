package com.intech.ai.controller;

import com.intech.ai.modal.Employee;
import com.intech.ai.modal.LoginRequest;
import com.intech.ai.service.EmployeeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1")
public class LoginController {

    private final EmployeeService service;

    public LoginController(EmployeeService service) {
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
