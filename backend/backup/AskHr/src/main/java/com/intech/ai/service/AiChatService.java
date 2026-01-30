package com.intech.ai.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.concurrent.CompletableFuture;

@Service
public class AiChatService {

    @Autowired
    private final ChatClient chatClient;

    public AiChatService(ChatClient.Builder builder) {
        this.chatClient = builder.build();
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

    public Flux<String> askStream(String prompt) {
        System.out.println("🔥 Calling Ollama with prompt: " + prompt);
        return chatClient.prompt().user(prompt).stream().content();
    }
}