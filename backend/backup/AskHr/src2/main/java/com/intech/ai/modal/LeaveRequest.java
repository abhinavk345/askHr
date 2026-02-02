package com.intech.ai.modal;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "leave_requests")
public class LeaveRequest {

    @Id
    @GeneratedValue
    private UUID id;

    private String employeeId;
    private String leaveType;

    private LocalDate fromDate;
    private LocalDate toDate;

    private int totalDays;

    private String reason;
    private String status; // APPLIED, APPROVED, REJECTED

    private LocalDateTime appliedAt;

    // getters & setters
}
