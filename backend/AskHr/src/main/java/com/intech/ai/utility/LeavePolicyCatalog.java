package com.intech.ai.utility;

import java.util.List;

public class LeavePolicyCatalog {

    public static final List<String> LEAVE_TYPES = List.of(
            "Need Based Leave",
            "Planned Leave",
            "Paternity Leave",
            "Maternity Leave",
            "Project Leave",
            "Leave Without Pay",
            "Election Leave",
            "Birthday Leave"
    );

    public static String summary() {
        StringBuilder sb = new StringBuilder();
        sb.append("Leave Policy Types (Quick List):\n");
        for (int i = 0; i < LEAVE_TYPES.size(); i++) {
            sb.append(i + 1).append(". ").append(LEAVE_TYPES.get(i)).append("\n");
        }
        sb.append("\nAsk: \"Explain <leave type>\" to get details.");
        return sb.toString();
    }
}
