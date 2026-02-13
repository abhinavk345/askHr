package com.intech.ai.tools;

import com.intech.ai.modal.LeaveBalance;
import com.intech.ai.service.LeaveService;
import com.intech.ai.service.ToolAuditService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Component
public class LeaveTools {

    private final LeaveService leaveService;
    private final ToolAuditService auditService;

    public LeaveTools(
            LeaveService leaveService,
            ToolAuditService auditService) {
        this.leaveService = leaveService;
        this.auditService = auditService;
    }

    // ============================================
    // GET LEAVE BALANCE
    // ============================================

    @Tool(description = "Get leave balance of an employee")
    public LeaveBalance getLeaveBalance(String employeeId) {

        try {
            LeaveBalance result = leaveService.getLeaveBalance(employeeId);

            auditService.logSuccess(
                    "getLeaveBalance",
                    employeeId,
                    employeeId,
                    result);

            return result;

        } catch (Exception ex) {

            auditService.logFailure(
                    "getLeaveBalance",
                    employeeId,
                    employeeId,
                    ex);

            throw ex;
        }
    }

    // ============================================
    // APPLY LEAVE
    // ============================================

    @Tool(description = "Apply leave for an employee")
    public String applyLeave(
            @ToolParam(description = "Employee ID") String employeeId,
            @ToolParam(description = "Leave type e.g. Casual, Sick, Paternity, Need Based") String leaveType,
            @ToolParam(description = "Leave start date (yyyy-MM-dd)") String fromDate,
            @ToolParam(description = "Leave end date (yyyy-MM-dd) Optional") String toDate,
            @ToolParam(description = "Reason for leave") String reason,
            @ToolParam(description = "Original user message") String originalMessage
    ) {

        Map<String, Object> input = new HashMap<>();
        input.put("employeeId", employeeId);
        input.put("leaveType", leaveType);
        input.put("fromDate", fromDate);
        input.put("toDate", toDate);
        input.put("reason", reason);

        try {

            LocalDate parsedFromDate = LocalDate.parse(fromDate);

            // 🔥 Make toDate optional
            LocalDate parsedToDate = null;

            if (toDate != null && !toDate.isBlank()) {
                parsedToDate = LocalDate.parse(toDate);
            }

            leaveService.applyLeave(
                    employeeId,
                    leaveType,
                    parsedFromDate,
                    parsedToDate,      // may be null
                    reason,
                    originalMessage   // 🔥 pass full message
            );

            auditService.logSuccess(
                    "applyLeave",
                    employeeId,
                    input,
                    "LEAVE_APPLIED"
            );

            return "✅ Leave applied successfully";

        } catch (Exception ex) {

            auditService.logFailure(
                    "applyLeave",
                    employeeId,
                    input,
                    ex
            );

            throw ex;
        }
    }
}