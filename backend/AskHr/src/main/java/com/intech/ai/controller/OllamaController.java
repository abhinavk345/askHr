package com.intech.ai.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1")
public class OllamaController {


    private final ChatClient chatClient;

    public OllamaController(ChatClient chatClient) {
        this.chatClient = chatClient;

    }

    //    @GetMapping("/chat")
//    public Flux<String> chat(@RequestParam String message) {
//        log.info("Inside method chat with :{}", message);
//        return chatClient.prompt(message).stream().content();
//    }

//    @GetMapping("/chat")
//    public Flux<String> chat(@RequestParam String message) {
//        log.info("Inside method chat with :{}", message);
//        return chatClient
//                .prompt(message)
//                .system("""
//                                                You are an internal IT helpdesk assistant. Your role is to assist\s
//                                                employees with IT-related issues such as resetting passwords,\s
//                                                unlocking accounts, and answering questions related to IT policies.
//                                                If a user requests help with anything outside of these\s
//                                                responsibilities, respond politely and inform them that you are\s
//                                                only able to assist with IT support tasks within your defined scope.
//                        """)
//                .user(message)
//                .stream()
//                .content();
//
//    }



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
