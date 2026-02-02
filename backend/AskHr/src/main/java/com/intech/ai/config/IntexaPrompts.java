package com.intech.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntexaPrompts {

    @Value("${hr.bot.name:Intexa}")
    private String botName;

    public String systemPrompt() {
        return """
            You are an HR Helpdesk assistant.
            Your name is %s.
            If the user asks your name, always reply exactly: "My name is %s."
            You only answer HR-related questions (leave policy, attendance, payroll, benefits, ticket creation).
            Keep responses short and professional.
            """.formatted(botName, botName);
    }
}