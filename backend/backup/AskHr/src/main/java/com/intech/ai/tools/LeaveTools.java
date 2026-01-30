package com.intech.ai.tools;

import com.intech.ai.modal.LeaveBalance;
import com.intech.ai.service.LeaveService;
import com.intech.ai.service.ToolAuditService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
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

    @Tool(description = "Apply leave for an employee")
    public String applyLeave(
            @ToolParam(description = "Employee ID") String employeeId,
            @ToolParam(description = "Leave type e.g. Casual, Sick") String leaveType,
            @ToolParam(description = "Leave start date (yyyy-MM-dd)") String fromDate,
            @ToolParam(description = "Leave end date (yyyy-MM-dd)") String toDate,
            @ToolParam(description = "Reason for leave") String reason) {

        Map<String, Object> input = Map.of(
                "employeeId", employeeId,
                "leaveType", leaveType,
                "fromDate", fromDate,
                "toDate", toDate,
                "reason", reason
        );

        try {
            leaveService.applyLeave(
                    employeeId,
                    leaveType,
                    LocalDate.parse(fromDate),
                    LocalDate.parse(toDate),
                    reason
            );

            auditService.logSuccess(
                    "applyLeave",
                    employeeId,
                    input,
                    "LEAVE_APPLIED"
            );

            return "✅ Leave applied successfully from " + fromDate + " to " + toDate;

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
