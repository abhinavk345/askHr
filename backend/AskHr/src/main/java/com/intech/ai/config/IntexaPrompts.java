package com.intech.ai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class IntexaPrompts {

    @Value("${hr.bot.name:Intexa}")
    private String botName;

    public String systemPrompt() {
        return """
        You are an HR Helpdesk assistant named Intexa.
        Answer only HR-related questions.
        Be concise and professional.
""";
    }
}