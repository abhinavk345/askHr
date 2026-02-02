package com.intech.ai.config;

import jakarta.annotation.PostConstruct;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Component;

@Component
public class OllamaWarmup {

    private final ChatClient chatClient;

    public OllamaWarmup(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    @PostConstruct
    public void warmUp() {
        chatClient.prompt()
                .user("Hi")
                .call();
        System.out.println("🔥 Ollama model warmed up");
    }
}
