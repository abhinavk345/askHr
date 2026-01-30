package com.intech.ai.modal;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@AllArgsConstructor
@NoArgsConstructor
@Data
@Entity
@Table(name = "tool_audit_log")
public class ToolAuditLog {

    @Id
    @GeneratedValue
    private UUID id;

    private String toolName;
    private String employeeId;

    @Column(columnDefinition = "TEXT")
    private String input;

    @Column(columnDefinition = "TEXT")
    private String output;

    private String status;
    private LocalDateTime executedAt;
}
