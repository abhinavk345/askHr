package com.intech.ai.repository;

import com.intech.ai.modal.ToolAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ToolAuditLogRepository  extends JpaRepository<ToolAuditLog, UUID> {}