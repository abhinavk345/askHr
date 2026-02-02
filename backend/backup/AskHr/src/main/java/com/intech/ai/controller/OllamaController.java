package com.intech.ai.controller;

import com.intech.ai.service.QueryService;
import com.intech.ai.utility.HRUtility;
import com.intech.ai.utility.IntentDetector;
import lombok.extern.slf4j.Slf4j;
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

        log.info("Search chat request: {}", message);
        String lowerMessage = message.toLowerCase().trim();

        // 1️⃣ GREETINGS
        if (HRUtility.isGreeting(message)) {
            return Flux.just("Hello! 👋 How can I help you with HR-related queries today?");
        }

        // 2️⃣ POLITE MESSAGES
        if (HRUtility.isPoliteMessage(message)) {
            if (lowerMessage.contains("thanks")) {
                return Flux.just("You're welcome! 😊");
            } else if (lowerMessage.contains("nice to meet you") || lowerMessage.contains("meet again")) {
                return Flux.just("Hello! Nice to meet you 😊");
            } else if (lowerMessage.contains("how are you")) {
                return Flux.just("Hello! I am fine, what about you? Thanks for asking.\nTell me what I can do for you? 😊");
            } else if (lowerMessage.contains("bye") || lowerMessage.contains("take care") || lowerMessage.contains("goodbye")) {
                return Flux.just("Take care! 👋");
            } else {
                return Flux.just("Glad to assist you!");
            }
        }

        // 3️⃣ LEAVE / TICKET REQUESTS
        if (IntentDetector.isLeaveOrTicketRequest(message)) {
            return handleLeaveOrTicket(lowerMessage);
        }

        // 4️⃣ HR POLICY / OTHER QUERIES → delegate to QueryService + AI
        return queryService.handlePolicyQuery(message, emailId);
    }

    /* ================= PRIVATE HELPERS ================= */

    private Flux<String> handleLeaveOrTicket(String lowerMessage) {

        // Action-based → static
        if (lowerMessage.contains("apply") || lowerMessage.contains("raise ticket")) {
            return Flux.just(
                    "You can apply for leave or raise a ticket via the HR portal: 🌐 https://ion.ics-global.in/odoo/time-off"
            );
        }

        return queryService.handlePolicyQuery(lowerMessage, null);
    }
}
