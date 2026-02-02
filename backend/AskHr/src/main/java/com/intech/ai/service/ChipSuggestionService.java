package com.intech.ai.service;

import com.intech.ai.dtos.ChipRequest;
import com.intech.ai.dtos.ChipResponse;
import com.intech.ai.dtos.Message;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ChipSuggestionService {

    private final ChatClient chatClient;

    public ChipSuggestionService(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public ChipResponse generateChips(ChipRequest request) {

        String context = request.conversation()
                .stream()
                .map(Message::content)
                .collect(Collectors.joining(" "));

        // 🔹 First try rule-based matching (fast + cheap)
        List<String> ruleBased = ruleBasedChips(context);
        if (!ruleBased.isEmpty()) {
            return new ChipResponse(ruleBased);
        }

        // 🔹 Fallback to AI-generated chips
        return new ChipResponse(aiGeneratedChips(context));
    }

    private List<String> ruleBasedChips(String context) {
        context = context.toLowerCase();

        if (context.contains("leave")) {
            return List.of("Casual leave", "Sick leave", "Apply leave", "Leave balance");
        }
        if (context.contains("salary")) {
            return List.of("Download slip", "CTC breakup", "Tax deduction");
        }
        if (context.contains("attendance")) {
            return List.of("Missed punch", "Regularization");
        }
        return List.of();
    }

    private List<String> aiGeneratedChips(String context) {

        String prompt = """
        You are an HR helpdesk assistant.
        Based on the conversation below, generate 5 short suggestion chips.
        Chips must be 1-3 words, HR-related, and actionable.

        Conversation:
        %s

        Return only a comma-separated list.
        """.formatted(context);

        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();

        return Arrays.stream(response.split(","))
                .map(String::trim)
                .limit(6)
                .toList();
    }
}
