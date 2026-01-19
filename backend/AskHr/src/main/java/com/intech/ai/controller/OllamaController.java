package com.intech.ai.controller;

import com.intech.ai.utility.HRUtility;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
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
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:3000")
public class OllamaController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/promptTemplates/system-greeting.st")
    private Resource greetingPrompt;

    @Value("classpath:/promptTemplates/system-no-docs.st")
    private Resource noDocsPrompt;

    @Value("classpath:/promptTemplates/system-with-docs.st")
    private Resource withDocsPrompt;

    @Value("classpath:/promptTemplates/system-fallback.st")
    private Resource fallbackPrompt;

    public OllamaController(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }

    @GetMapping("/search/chat")
    public Flux<String> searchChat(
            @RequestHeader(value = "emailId", required = false) String emailId,
            @RequestParam String message) {

        log.info("Search chat request: {}", message);

        try {
            // 1️⃣ Greeting → static LLM call (no vector)
            if (HRUtility.isGreeting(message)) {
                return executePrompt(greetingPrompt, null, emailId, message);
            }

            // 2️⃣ Not HR related
            if (!HRUtility.isHRMessage(message)) {
                return executePrompt(noDocsPrompt, null, emailId, message);
            }

            // 3️⃣ Vector search
            List<Document> docs = vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query(message)
                            .topK(5)
                            .similarityThreshold(0.6)
                            .build()
            );

            if (docs.isEmpty()) {
                return executePrompt(noDocsPrompt, null, emailId, message);
            }

            // 4️⃣ Docs found
            String context = docs.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            return executePrompt(withDocsPrompt, context, emailId, message);

        } catch (Exception ex) {
            log.error("Chat processing failed", ex);
            return executePrompt(fallbackPrompt, null, emailId, message);
        }
    }

    // 🔧 SINGLE SAFE EXECUTION METHOD
    private Flux<String> executePrompt(
            Resource systemPrompt,
            String documents,
            String emailId,
            String userMessage) {

        var prompt = chatClient.prompt()
                .system(ps -> {
                    ps.text(systemPrompt);
                    if (documents != null) {
                        ps.param("documents", documents);
                    }
                })
                .user(userMessage);

        if (emailId != null && !emailId.isBlank()) {
            prompt.advisors(a -> a.param(ChatMemory.CONVERSATION_ID, emailId));
        }

        return prompt.stream().content();
    }
}