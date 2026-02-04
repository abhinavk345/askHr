package com.intech.ai.enums;

public enum UserIntent {
    GREETING,
    LEAVE_CREATE,
    LEAVE_STATUS,
    TICKET_UPDATE,
    POLICY_QUERY,
    GENERAL_CHAT;

    public boolean isStructured() {
        return this != GENERAL_CHAT;
    }
}