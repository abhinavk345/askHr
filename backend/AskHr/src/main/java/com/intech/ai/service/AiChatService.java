package com.intech.ai.service;

import com.intech.ai.config.IntexaPrompts;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

@Service
public class AiChatService {

    private final ChatClient chatClient;
    private final IntexaPrompts intexaPrompts;

    public AiChatService(ChatClient chatClient, IntexaPrompts intexaPrompts) {
        this.chatClient = chatClient;
        this.intexaPrompts = intexaPrompts;
    }

    @Async
    public CompletableFuture<String> ask(String prompt) {
        return CompletableFuture.completedFuture(
                chatClient
                        .prompt()
                        .user(prompt)
                        .call()
                        .content());
    }

    public String askStream(String prompt) {
        System.out.println("🔥 Calling Ollama with prompt: " + prompt);
        String systemPrompt = intexaPrompts.systemPrompt();
        return chatClient
                .prompt()
                .system(systemPrompt)
                .user(prompt)
                .call().content();
    }
}