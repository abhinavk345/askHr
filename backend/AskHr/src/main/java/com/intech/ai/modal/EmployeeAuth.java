package com.intech.ai.modal;

import jakarta.persistence.*;
import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(
    name = "employee_auth",
    indexes = {
        @Index(name = "idx_auth_employee_id", columnList = "employeeId", unique = true),
        @Index(name = "idx_auth_username", columnList = "username", unique = true)
    }
)
public class EmployeeAuth {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK by business key (not DB id)
    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(nullable = false, unique = true)
    private String username; // email or employeeId

    @JsonIgnore
    @Column(name = "password", nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // EMPLOYEE, MANAGER, HR, ADMIN

    @Column(nullable = false)
    private boolean enabled = true;

    private LocalDateTime lastLoginAt;
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        if (role == null) role = "EMPLOYEE";
    }
}
