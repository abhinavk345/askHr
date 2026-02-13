package com.intech.ai.service;

import com.intech.ai.modal.LeaveBalance;
import com.intech.ai.modal.LeaveRequest;
import com.intech.ai.repository.LeaveBalanceRepository;
import com.intech.ai.repository.LeaveRequestRepository;
import com.intech.ai.utility.NumberWordConverter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class LeaveService {

    private final LeaveBalanceRepository balanceRepo;
    private final LeaveRequestRepository requestRepo;

    public LeaveService(LeaveBalanceRepository balanceRepo,
                        LeaveRequestRepository requestRepo) {
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
            String reason,
            String originalMessage   // 🔥 added
    ) {

        // 🔥 Auto-calculate duration if toDate is null
        if (toDate == null) {
            toDate = extractDuration(originalMessage, fromDate);

            if (toDate == null) {
                toDate = fromDate; // fallback 1 day
            }
        }

        // Validate
        if (fromDate == null) {
            throw new IllegalArgumentException("From date is required");
        }

        if (fromDate.isAfter(toDate)) {
            throw new IllegalArgumentException("From date cannot be after To date");
        }

        int totalDays = (int) ChronoUnit.DAYS.between(fromDate, toDate) + 1;

        LeaveBalance balance = balanceRepo.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Leave balance not found"));

        // 🔥 Support More Leave Types
        switch (leaveType.toUpperCase()) {

            case "CASUAL":
                deductCasual(balance, totalDays);
                break;

            case "SICK":
                deductSick(balance, totalDays);
                break;

            case "PATERNITY":
            case "NEED BASED":
                // No deduction or custom logic if needed
                break;

            default:
                throw new RuntimeException("Unsupported leave type: " + leaveType);
        }

        balanceRepo.save(balance);

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

    // ==========================
    // 🔥 Duration Extractor
    // ==========================

    private LocalDate extractDuration1(String message, LocalDate fromDate) {

        if (message == null || fromDate == null) return null;

        message = message.toLowerCase();

        Map<String, Integer> numberWords = Map.of(
                "one", 1, "two", 2, "three", 3,
                "four", 4, "five", 5, "six", 6,
                "seven", 7, "eight", 8, "nine", 9, "ten", 10
        );

        // Numeric like "5 days"
        Pattern numericPattern = Pattern.compile("(\\d+)\\s*(day|days)");
        Matcher numericMatcher = numericPattern.matcher(message);

        if (numericMatcher.find()) {
            int days = Integer.parseInt(numericMatcher.group(1));
            return fromDate.plusDays(days - 1);
        }

        // Word based like "five day"
        for (Map.Entry<String, Integer> entry : numberWords.entrySet()) {
            if (message.contains(entry.getKey() + " day")) {
                return fromDate.plusDays(entry.getValue() - 1);
            }
        }

        return null;
    }
    private LocalDate extractDuration2(String message, LocalDate fromDate) {

        if (message == null || fromDate == null) return null;

        message = message.toLowerCase().trim();

        Map<String, Integer> numberWords = Map.of(
                "one", 1, "two", 2, "three", 3,
                "four", 4, "five", 5, "six", 6,
                "seven", 7, "eight", 8, "nine", 9, "ten", 10
        );

        // ==============================
        // 1️⃣ Numeric: 5 day / 5 days
        // ==============================
        Pattern numericPattern = Pattern.compile("(\\d+)\\s*day[s]?");
        Matcher numericMatcher = numericPattern.matcher(message);

        if (numericMatcher.find()) {
            int days = Integer.parseInt(numericMatcher.group(1));
            return fromDate.plusDays(days - 1);
        }

        // ==============================
        // 2️⃣ Word: five day / five days
        // ==============================
        Pattern wordPattern = Pattern.compile(
                "(one|two|three|four|five|six|seven|eight|nine|ten)\\s*day[s]?"
        );

        Matcher wordMatcher = wordPattern.matcher(message);

        if (wordMatcher.find()) {
            String word = wordMatcher.group(1);
            Integer days = numberWords.get(word);
            if (days != null) {
                return fromDate.plusDays(days - 1);
            }
        }

        return null;
    }
    private LocalDate extractDuration(String message, LocalDate fromDate) {

        if (message == null || fromDate == null) {
            return null;
        }

        message = message.toLowerCase().trim();

        // Match: 5 day, 5 days, five day, five days
        Pattern pattern = Pattern.compile("(\\w+)\\s*day[s]?");
        Matcher matcher = pattern.matcher(message);

        if (matcher.find()) {

            String durationText = matcher.group(1);

            Integer days = NumberWordConverter.convert(durationText);

            if (days != null && days > 0) {
                return fromDate.plusDays(days - 1);
            }
        }

        return null;
    }
    // ==========================
    // 🔥 Balance Deduction Logic
    // ==========================

    private void deductCasual(LeaveBalance balance, int days) {
        if (balance.getCasualLeaves() < days) {
            throw new RuntimeException("Insufficient casual leave balance");
        }
        balance.setCasualLeaves(balance.getCasualLeaves() - days);
    }

    private void deductSick(LeaveBalance balance, int days) {
        if (balance.getSickLeaves() < days) {
            throw new RuntimeException("Insufficient sick leave balance");
        }
        balance.setSickLeaves(balance.getSickLeaves() - days);
    }
}