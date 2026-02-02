package com.intech.ai.controller;

import com.intech.ai.service.QueryService;
import com.intech.ai.utility.HRUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1")
public class OllamaController {

    private final QueryService queryService;

    public OllamaController(QueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/search/chat")
    public Flux<String> searchChat(
            @RequestHeader(value = "emailId", required = false) String emailId,
            @RequestParam String message) {

        log.info("Chat request: {}", message);

        // 1️⃣ Greetings (static, no AI)
        if (HRUtility.isGreeting(message)) {
            return Flux.just("Hello! 👋 How can I help you with HR-related queries today?");
        }

        // 2️⃣ Polite messages (static)
        if (HRUtility.isPoliteMessage(message)) {
            return Flux.just("I am fine. And you're welcome! 😊");
        }

        // 3️⃣ EVERYTHING ELSE → Service decides (policy vs action)
        return queryService.handleUserQuery(message, emailId);
    }
}