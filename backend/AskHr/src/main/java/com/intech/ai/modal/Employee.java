package com.intech.ai.modal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(
        name = "employee",
        indexes = {
                @Index(name = "idx_employee_employee_id", columnList = "employeeId", unique = true),
                @Index(name = "idx_employee_email", columnList = "email", unique = true)
        }
)
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_id", nullable = false, unique = true)
    private String employeeId;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false, unique = true)
    private String email;

    private String department;
    private String designation;

    private String managerEmployeeId;

    @Column(nullable = false)
    private String status; // ACTIVE, INACTIVE, EXITED

    private LocalDateTime dateOfJoining;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /* ================= JPA Hooks ================= */

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (status == null) status = "ACTIVE";
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
