package com.intech.ai.controller;

import com.intech.ai.service.QueryService;
import com.intech.ai.utility.HRUtility;
import com.intech.ai.utility.IntentDetector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1")
public class OllamaController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;
    private final QueryService queryService;

    public OllamaController(ChatClient.Builder chatClientBuilder,
                            VectorStore vectorStore, QueryService queryService) {

        this.vectorStore = vectorStore;

        // ✅ NO CHAT MEMORY (avoids slow prompt growth)
        this.chatClient = chatClientBuilder.build();
        this.queryService = queryService;
    }

    /* ================= PROMPTS ================= */

    @Value("classpath:/promptTemplates/system-greeting.st")
    private Resource greetingPrompt;

    @Value("classpath:/promptTemplates/system-no-docs.st")
    private Resource noDocsPrompt;

    @Value("classpath:/promptTemplates/system-with-docs.st")
    private Resource withDocsPrompt;

    @Value("classpath:/promptTemplates/system-fallback.st")
    private Resource fallbackPrompt;

    /* ================= SEARCH CHAT ================= */

    @GetMapping("/search/chat")
    public Flux<String> searchChat(
            @RequestHeader(value = "emailId", required = false) String emailId,
            @RequestParam String message) {
        log.info("Search chat request: {}", message);
        String lowerMessage = message.toLowerCase().trim();
        // 1️⃣ GREETING BYPASS
        if (HRUtility.isGreeting(message)) {
            return Flux.just("Hello! 👋 How can I help you with HR-related queries today?");
        }
        // 2️⃣ Leave / Ticket Requests
        if (IntentDetector.isLeaveOrTicketRequest(message)) {
            return Flux.just("You can apply for leave or raise a ticket via the portal. ✅");
        }
        // 3️⃣ Courtesy / Polite Messages
        if (HRUtility.isPoliteMessage(message)) {
            if (lowerMessage.contains("thanks")) {
                return Flux.just("You're welcome! 😊");
            } else if (lowerMessage.contains("nice to meet you") || lowerMessage.contains("meet again")) {
                return Flux.just("Hello! Nice to meet you 😊");
            }else if (lowerMessage.contains("how are you ") || lowerMessage.contains("How are you?")) {
                return Flux.just("Hello! I am fine ,what about you. Thanks for asking.\n Tell me what can i do for you? 😊");
            } else if (lowerMessage.contains("bye") || lowerMessage.contains("take care") || lowerMessage.contains("goodbye")) {
                return Flux.just("Take care! 👋");
            } else {
                return Flux.just("Glad to assist you!");
            }
        }
        // 4️⃣ HR Policy / Unknown Messages
        if (HRUtility.isHRMessage(message)) {
            // delegate to policy query service (possibly async / database / embeddings)
            return queryService.handlePolicyQuery(message, emailId);
        }
        // 5️⃣ Fallback for everything else
        return Flux.just("I’m sorry, this information is not available as per the HR Leave Policy.");
    }
}
