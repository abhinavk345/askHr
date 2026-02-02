/*
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

    public static boolean isLeaveCreationRequest(String message) {
        if (message == null) return false;

        String m = message.toLowerCase();

        boolean hasLeaveKeyword =
                m.contains("leave") || m.contains("leav"); // typo support

        boolean hasActionKeyword =
                m.contains("apply") ||
                        m.contains("create") ||
                        m.contains("book") ||
                        m.contains("raise") ||
                        m.contains("request") ||
                        m.contains("need");

        return hasLeaveKeyword && hasActionKeyword;
    }


    public static boolean isPolicyQuery(String message) {
        String m = message.toLowerCase();
        return m.contains("policy")
                || m.contains("hr")
                || m.contains("leave policy");
    }
}
*/

package com.intech.ai.utility;

import java.util.List;

public class IntentDetector {

    private static final List<String> ACTION_WORDS = List.of(
            "apply", "create", "book", "raise", "request", "need"
    );

    private static final List<String> LEAVE_WORDS = List.of(
            "leave", "leav", "leve"
    );

    public static boolean isLeaveCreationRequest(String message) {
        if (message == null) return false;

        String m = FuzzyTextUtil.normalize(message);

        // Fast path
        boolean hasLeave = m.contains("leave") || m.contains("leav");
        boolean hasAction = ACTION_WORDS.stream().anyMatch(m::contains);

        if (hasLeave && hasAction) return true;

        // Fuzzy fallback for typos
        boolean fuzzyLeave = FuzzyTextUtil.fuzzyContainsAny(message, LEAVE_WORDS, 1);
        boolean fuzzyAction = FuzzyTextUtil.fuzzyContainsAny(message, ACTION_WORDS, 1);

        return fuzzyLeave && fuzzyAction;
    }

    public static boolean isLeavePolicySummary(String message) {
        if (message == null) return false;
        String m = FuzzyTextUtil.normalize(message);

        return m.equals("leave policy")
                || m.equals("leave policies")
                || m.contains("list leave")
                || m.contains("leave types")
                || m.contains("types of leave");
    }
}
