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

    /**
     * 🔥 FAST synchronous call (USE THIS FOR POLICY, STATUS, CONFIRMATIONS)
     */
    public String askSync(String prompt) {
        return chatClient
                .prompt()
                .system(intexaPrompts.systemPrompt())
                .user(prompt)
                .call()
                .content();
    }

    /**
     * Async call (USE ONLY if you really need CompletableFuture)
     */
    @Async
    public CompletableFuture<String> ask(String prompt) {
        return CompletableFuture.completedFuture(
                askSync(prompt)
        );
    }

    /**
     * Streaming response (USE ONLY for chat-like UX)
     */
    public Flux<String> askStream(String prompt) {
        System.out.println("🔥 Calling Ollama with prompt");

        return chatClient
                .prompt()
                .system(intexaPrompts.systemPrompt())
                .user(prompt)
                .stream()
                .content();
    }
}
