package com.intech.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intech.ai.modal.ToolAuditLog;
import com.intech.ai.repository.ToolAuditLogRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ToolAuditService {

    private final ToolAuditLogRepository repo;
    private final ObjectMapper mapper = new ObjectMapper();

    public ToolAuditService(ToolAuditLogRepository repo) {
        this.repo = repo;
    }

    public void logSuccess(
            String toolName,
            String employeeId,
            Object input,
            Object output) {

        save(toolName, employeeId, input, output, "SUCCESS");
    }

    public void logFailure(
            String toolName,
            String employeeId,
            Object input,
            Exception ex) {

        save(toolName, employeeId, input, ex.getMessage(), "FAILED");
    }

    private void save(
            String toolName,
            String employeeId,
            Object input,
            Object output,
            String status) {

        try {
            ToolAuditLog log = new ToolAuditLog();
            log.setToolName(toolName);
            log.setEmployeeId(employeeId);
            log.setInput(mapper.writeValueAsString(input));
            log.setOutput(mapper.writeValueAsString(output));
            log.setStatus(status);
            log.setExecutedAt(LocalDateTime.now());

            repo.save(log);
        } catch (Exception ignored) {}
    }
}
