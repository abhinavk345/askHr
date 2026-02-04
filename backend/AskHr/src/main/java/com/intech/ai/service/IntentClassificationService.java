package com.intech.ai.service;

import com.intech.ai.enums.UserIntent;
import org.springframework.stereotype.Service;

@Service
public class IntentClassificationService {

    private final AiChatService aiChatService;

    public IntentClassificationService(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    public UserIntent detectIntentWithLLM(String message) {

        String prompt = """
        Classify the user's intent into ONE of:
        GREETING,
        LEAVE_CREATE,
        LEAVE_STATUS,
        POLICY_QUERY,
        GENERAL_CHAT.

        User message: "%s"

        Reply ONLY with the intent name.
        """.formatted(message);

        try {
            String response = aiChatService.askSync(prompt);
            return UserIntent.valueOf(response.trim().toUpperCase());
        } catch (Exception e) {
            return UserIntent.GENERAL_CHAT; // safe fallback
        }
    }
}
