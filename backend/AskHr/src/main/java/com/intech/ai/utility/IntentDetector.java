package com.intech.ai.utility;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intech.ai.utility.HRUtility.isGreeting;

public class IntentDetector {

    // More natural action words
    private static final List<String> ACTION_WORDS = List.of(
            "apply", "create", "book", "raise", "request", "need",
            "want", "take", "submit", "put"
    );

    // leave keyword typos
    private static final List<String> LEAVE_WORDS = List.of(
            "leave", "leav", "leve", "lave"
    );

    // leave type keywords (helps “need base leave tomorrow”)
    private static final List<String> LEAVE_TYPE_WORDS = List.of(
            "need based", "need base", "needbased", "nbl",
            "planned", "pl",
            "paternity", "father",
            "maternity", "mother",
            "lwp", "without pay"
    );

    // date related words
    private static final List<String> DATE_WORDS = List.of(
            "today", "tomorrow", "tmrw", "tomorow"
    );

    // duration related phrases
    private static final List<String> DURATION_WORDS = List.of(
            "1 day", "one day", "2 days", "two days", "half day"
    );

    private static final List<String> CANCEL_WORDS = List.of(
            "cancel", "stop", "exit", "abort", "quit", "never mind"
    );

    private static final List<String> POLICY_KEYWORDS = List.of(
            "policy", "leave policy", "maternity", "paternity",
            "attendance", "payroll", "salary", "benefits", "hr policy"
    );
    /**
     * Leave Creation Intent:
     * supports:
     *  - "apply leave"
     *  - "need base leave tomorrow"
     *  - "leave for 1 day"
     *  - typos: "leav", "leve", "tomorow"
     */
    public static boolean isLeaveCreationRequest(String message) {
        if (message == null || message.isBlank()) return false;

        if (isLeavePolicyQuery(message)) return false;

        String m = FuzzyTextUtil.normalize(message);
        if (isLeavePolicyQuery(message) || m.contains("leave policy") || m.contains("policy")) {
            return false;
        }
        // --- 1) Detect leave keyword
        boolean hasLeave =
                m.contains("leave") ||
                        LEAVE_WORDS.stream().anyMatch(m::contains) ||
                        FuzzyTextUtil.fuzzyContainsAny(m, LEAVE_WORDS, 1);

        if (!hasLeave) return false;

        // --- 2) Detect intent signals
        boolean hasAction =
                ACTION_WORDS.stream().anyMatch(m::contains) ||
                        FuzzyTextUtil.fuzzyContainsAny(m, ACTION_WORDS, 1);

        boolean hasLeaveType =
                LEAVE_TYPE_WORDS.stream().anyMatch(m::contains) ||
                        FuzzyTextUtil.fuzzyContainsAny(m, LEAVE_TYPE_WORDS, 2);

        boolean hasDate =
                DATE_WORDS.stream().anyMatch(m::contains) ||
                        FuzzyTextUtil.fuzzyContainsAny(m, DATE_WORDS, 2) ||
                        m.matches(".*\\d{4}-\\d{2}-\\d{2}.*"); // yyyy-MM-dd

        boolean hasDuration =
                DURATION_WORDS.stream().anyMatch(m::contains) ||
                        m.matches(".*\\b\\d+\\s*day(s)?\\b.*"); // "3 days", "1 day"

        // If leave keyword exists, and any of these signals exist -> leave request
        return hasAction || hasLeaveType || hasDate || hasDuration;
    }

    public static boolean isLeavePolicyQuery(String message) {
        if (message == null || message.isBlank()) return false;

        String m = FuzzyTextUtil.normalize(message);

        return m.equals("leave policy")
                || m.equals("leave policies")
                || m.contains("list leave")
                || m.contains("leave types")
                || m.contains("types of leave");
    }

    /* =============================
       Ticket intents (more natural)
       ============================= */

    public static boolean isTicketStatusRequest_backup(String message) {
        if (message == null || message.isBlank()) return false;

        String m = FuzzyTextUtil.normalize(message);

        // Natural queries: "check ticket", "ticket status", "status of ticket"
        boolean hasTicket = m.contains("ticket") || FuzzyTextUtil.fuzzyTokenMatch(m, "ticket", 1);
        boolean hasStatus = m.contains("status") || m.contains("check") || m.contains("track");

        return hasTicket && hasStatus;
    }

    public static boolean isTicketStatusRequest(String message) {
        if (message == null) return false;

        String normalized = message.toLowerCase();

        return normalized.contains("status")
                && normalized.matches(".*[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
    }

    public static boolean isTicketDeleteRequest(String message) {
        if (message == null || message.isBlank()) return false;

        String m = FuzzyTextUtil.normalize(message);

        boolean hasTicket = m.contains("ticket") || FuzzyTextUtil.fuzzyTokenMatch(m, "ticket", 1);

        boolean hasDelete =
                m.contains("delete") || m.contains("remove") || m.contains("cancel") ||
                        FuzzyTextUtil.fuzzyTokenMatch(m, "delete", 2) ||
                        FuzzyTextUtil.fuzzyTokenMatch(m, "remove", 2) ||
                        FuzzyTextUtil.fuzzyTokenMatch(m, "cancel", 2);

        return hasTicket && hasDelete;
    }

    public static boolean isTicketUpdateRequest(String message) {
        if (message == null || message.isBlank()) return false;

        String m = FuzzyTextUtil.normalize(message);

        boolean hasTicket = m.contains("ticket") || FuzzyTextUtil.fuzzyTokenMatch(m, "ticket", 1);

        boolean hasUpdate =
                m.contains("update") || m.contains("edit") || m.contains("modify") || m.contains("change") ||
                        FuzzyTextUtil.fuzzyTokenMatch(m, "update", 2) ||
                        FuzzyTextUtil.fuzzyTokenMatch(m, "edit", 1) ||
                        FuzzyTextUtil.fuzzyTokenMatch(m, "modify", 2);

        return hasTicket && hasUpdate;
    }

    public static boolean isNewIntent1(String message) {
        if (message == null) return false;

        String m = message.toLowerCase().trim();

        // question starters
        if (m.startsWith("what")
                || m.startsWith("how")
                || m.startsWith("when")
                || m.startsWith("why")) {
            return true;
        }

        // policy or ticket queries
        return isPolicyQuestion(m)
                || isTicketStatusRequest(m)
                || isTicketUpdateRequest(m)
                || isTicketDeleteRequest(m);
    }

    public static boolean isNewIntent(String message) {
        if (message == null || message.isBlank()) return false;

        String m = FuzzyTextUtil.normalize(message);

        // policy questions should override leave flow
        if (isLeavePolicyQuery(message) || m.contains("policy")) return true;

        // ticket actions override leave flow
        return isTicketStatusRequest(message) || isTicketUpdateRequest(message) || isTicketDeleteRequest(message);
    }

    public static boolean isCancel(String message) {
        if (message == null) return false;

        String m = message.toLowerCase().trim();
        return CANCEL_WORDS.stream().anyMatch(m::contains);
    }

    public static boolean isPolicyQuestion(String message) {
        if (message == null) return false;

        String m = message.toLowerCase();
        return POLICY_KEYWORDS.stream().anyMatch(m::contains);
    }

    public static boolean hasDateRange(String message) {
        String m = message.toLowerCase();
        return m.contains("to")
                || m.contains("till")
                || m.contains("until")
                || m.contains("next");
    }

    public static Integer extractDurationDays(String message) {
        Pattern p = Pattern.compile("(\\d+)\\s*(day|days)");
        Matcher m = p.matcher(message.toLowerCase());

        if (m.find()) {
            return Integer.parseInt(m.group(1));
        }
        return null;
    }

    public static boolean isTicketStatusExportRequest(String message) {
        if (message == null || message.isBlank()) return false;
        String m = FuzzyTextUtil.normalize(message);

        return m.contains("download status")
                || m.contains("status file")
                || m.contains("export status")
                || m.contains("ticket report")
                || m.contains("ticket status excel")
                || m.contains("ticket status file");
    }
}
