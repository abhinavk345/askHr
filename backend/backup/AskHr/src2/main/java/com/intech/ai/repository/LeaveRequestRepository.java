package com.intech.ai.repository;

import com.intech.ai.modal.LeaveRequest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface LeaveRequestRepository
        extends JpaRepository<LeaveRequest, UUID> {
}
