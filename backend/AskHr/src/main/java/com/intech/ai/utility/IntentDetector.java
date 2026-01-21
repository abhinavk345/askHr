package com.intech.ai.utility;

public class IntentDetector {

    public static boolean isLeaveOrTicketRequest(String message) {
        String m = message.toLowerCase();
        return m.contains("leave")
                || m.contains("jira")
                || m.contains("ticket")
                || m.contains("apply leave");
    }

    public static boolean isPolicyQuery(String message) {
        String m = message.toLowerCase();
        return m.contains("policy")
                || m.contains("hr")
                || m.contains("leave policy");
    }
}
