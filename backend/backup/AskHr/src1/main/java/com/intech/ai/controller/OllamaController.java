package com.intech.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@CrossOrigin(origins = "http://localhost:3001")
@RestController
@RequestMapping("/api/v1")
public class OllamaController {


    private final ChatClient chatClient;

    public OllamaController(ChatClient chatClient) {
        this.chatClient = chatClient;

    }


    @Value("classpath:/promptTemplates/leavePolicy.st")
    Resource hrTemplates;

    @GetMapping("/chat")
    public Flux<String> chat(@RequestHeader(value = "emailId", required = false) String emailId
                                            ,@RequestParam String message) {
        log.info("Inside method chat with :{}", message);
        if(!emailId.isEmpty()) {
            return chatClient
                    .prompt()
                    .system(hrTemplates)
                    .user(message)
                    .advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, emailId))
                    .stream()
                    .content();
        }else{
            return chatClient
                    .prompt()
                    .system(hrTemplates)
                    .user(message)
                    .stream()
                    .content();
        }

    }


}
