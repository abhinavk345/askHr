package com.intech.ai.utility;

import java.util.List;

public class IntentDetector {

    private static final List<String> LEAVE_PHRASES = List.of(
            "apply leave",
            "create leave",
            "raise leave",
            "book leave",
            "leave request",
            "request leave",
            "need leave",
            "want leave",
            "take leave"
    );

    public static boolean isLeaveOrTicketRequest(String message) {
        String m = message.toLowerCase();
        return m.contains("leave")
                || m.contains("jira")
                || m.contains("ticket")
                || m.contains("apply leave");
    }

    public static boolean isLeavePolicySummary(String message) {
        if (message == null) return false;
        String m = FuzzyTextUtil.normalize(message);

        return m.equals("leave policy")
                || m.equals("leave policies")
                || m.contains("leave types")
                || m.contains("types of leave")
                || m.contains("list leave");
    }

    public static boolean isLeaveCreationRequest(String message) {
        if (message == null) return false;

        String m = FuzzyTextUtil.normalize(message);

        // normal contains
        for (String p : LEAVE_PHRASES) {
            if (m.contains(p)) return true;
        }

        // fuzzy match for typos
        return FuzzyTextUtil.fuzzyContains(message, LEAVE_PHRASES, 2);
    }

    public static boolean isPolicyQuery(String message) {
        String m = message.toLowerCase();
        return m.contains("policy")
                || m.contains("hr")
                || m.contains("leave policy");
    }
}
