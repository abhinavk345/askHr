package com.intech.ai.service;

import com.intech.ai.modal.LeaveBalance;
import com.intech.ai.modal.LeaveRequest;
import com.intech.ai.repository.LeaveBalanceRepository;
import com.intech.ai.repository.LeaveRequestRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Service
public class LeaveService {


    private final LeaveBalanceRepository balanceRepo;
    private final LeaveRequestRepository requestRepo;

    public LeaveService(LeaveBalanceRepository repo, LeaveBalanceRepository balanceRepo, LeaveRequestRepository requestRepo) {
        this.balanceRepo = balanceRepo;
        this.requestRepo = requestRepo;

    }

    public LeaveBalance getLeaveBalance(String employeeId) {
        return balanceRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Leave balance not found"));
    }

    @Transactional
    public void applyLeave(
            String employeeId,
            String leaveType,
            LocalDate fromDate,
            LocalDate toDate,
            String reason) {

        // 1️⃣ Validate dates
        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("From date cannot be after To date");
        }

        int totalDays = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        // 2️⃣ Get leave balance
        LeaveBalance balance = balanceRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Leave balance not found"));

        // 3️⃣ Deduct balance
        if ("CASUAL".equalsIgnoreCase(leaveType)) {
            if (balance.getCasualLeaves() < totalDays) {
                throw new RuntimeException("Insufficient casual leave balance");
            }
            balance.setCasualLeaves(balance.getCasualLeaves() - totalDays);
        } else if ("SICK".equalsIgnoreCase(leaveType)) {
            if (balance.getSickLeaves() < totalDays) {
                throw new RuntimeException("Insufficient sick leave balance");
            }
            balance.setSickLeaves(balance.getSickLeaves() - totalDays);
        } else {
            throw new RuntimeException("Unsupported leave type");
        }

        // 4️⃣ Save updated balance
        balanceRepo.save(balance);

        // 5️⃣ Create leave request
        LeaveRequest request = new LeaveRequest();
        request.setEmployeeId(employeeId);
        request.setLeaveType(leaveType.toUpperCase());
        request.setFromDate(fromDate);
        request.setToDate(toDate);
        request.setTotalDays(totalDays);
        request.setReason(reason);
        request.setStatus("APPLIED");
        request.setAppliedAt(LocalDateTime.now());

        requestRepo.save(request);
    }
}

