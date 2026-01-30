package com.intech.ai.utility;

public class IntentDetector {

    public static boolean isLeaveOrTicketRequest(String message) {
        String m = message.toLowerCase();
        return m.contains("leave")
                || m.contains("jira")
                || m.contains("ticket")
                || m.contains("apply leave");
    }

    public static boolean isLeaveCreationRequest(String message) {
        if (message == null) return false;

        String m = message.toLowerCase().trim();

        return m.contains("apply leave")
                || m.contains("create leave")
                || m.contains("raise leave")
                || m.contains("book leave")
                || m.contains("confirm leave");
    }

    public static boolean isLeavePolicySummary(String message) {
        if (message == null) return false;
        String m = message.toLowerCase().trim();

        return m.equals("leave policy")
                || m.equals("leave policies")
                || m.contains("list leave")
                || m.contains("types of leave")
                || m.contains("leave types");
    }

    public static boolean isPolicyQuery(String message) {
        String m = message.toLowerCase();
        return m.contains("policy")
                || m.contains("hr")
                || m.contains("leave policy");
    }
}
