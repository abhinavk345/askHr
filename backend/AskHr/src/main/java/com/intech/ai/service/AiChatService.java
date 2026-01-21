package com.intech.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class AiChatService {

    private final ChatClient chatClient;

    public AiChatService(ChatClient chatClient) {
        this.chatClient = chatClient;
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
}