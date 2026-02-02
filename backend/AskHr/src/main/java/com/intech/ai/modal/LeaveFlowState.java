package com.intech.ai.modal;

import com.intech.ai.enums.LeaveStep;
import lombok.Data;

import java.time.LocalDate;

@Data
public class LeaveFlowState {
    private LeaveStep step; // TYPE, FROM_DATE, TO_DATE, REASON, CONFIRM
    private String leaveType;
    private LocalDate fromDate;
    private LocalDate toDate;
    private String reason;
}

