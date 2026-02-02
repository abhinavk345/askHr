package com.intech.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AiChatClientBuilderConfig {

    private final ChatClient chatClient;

    public AiChatClientBuilderConfig(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @Bean
    public ChatClient chatClient(ChatClient.Builder chatModel) {
        return chatClient;
    }
}
