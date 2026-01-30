package com.intech.ai.modal;

import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveFlowState {
    private String step; // TYPE, FROM_DATE, TO_DATE, REASON, CONFIRM
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
}
