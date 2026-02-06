package com.intech.ai.utility;

public class TicketUtil {

    private static final String TICKET_ID_REGEX =
            "\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b";

    public static boolean containsTicketId(String message) {
        return message != null && message.matches(".*" + TICKET_ID_REGEX + ".*");
    }

    public static String extractTicketId(String message) {
        return message.replaceAll(".*(" + TICKET_ID_REGEX + ").*", "$1");
    }
}