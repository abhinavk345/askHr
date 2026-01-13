package com.intech.ai.controller;

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
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api/v1")
public class OllamaController {

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("classpath:/promptTemplates/systemPrompt.st")
    private Resource hrSystemPromptTemplates;

    // Precompiled HR Pattern for filtering
    private static final Pattern HR_PATTERN = Pattern.compile(
            "(?i).*(" +
                    "hello|how|are|you|hr|human resources|employee|employment|" +
                    "leave|leaves|privilege leave|pl|need based leave|nbl|leave without pay|lwp|" +
                    "maternity|paternity|bereavement|birthday leave|medical leave|comp off|compensatory off|" +
                    "holiday|public holiday|annual holiday|weekly off|week off|" +
                    "attendance|time & attendance|working hours|work timings|shift|break time|" +
                    "late entry|early leaving|short working|out of office|" +
                    "policy|code of conduct|business ethics|office conduct|" +
                    "disciplinary action|penalty|violation|misconduct|" +
                    "anti gossip|gossip|workplace behavior|" +
                    "probation|confirmation|promotion|transfer|" +
                    "performance appraisal|performance review|pip|performance improvement|" +
                    "resignation|notice period|separation|termination|" +
                    "conflict of interest|moonlighting|outside employment|dual employment|" +
                    "confidentiality|non disclosure|nda|data privacy|personnel record|" +
                    "sexual harassment|harassment|posh|icc committee|" +
                    "workplace violence|safety|health|drug|alcohol|substance abuse|" +
                    "dress code|office attire|business casual|id card|" +
                    "company assets|asset misuse|" +
                    "training|learning|development|certification|reimbursement|" +
                    "referral|referral bonus|benefits|" +
                    "grievance|complaint|escalation|hr approval|manager approval" +
                    ").*"
    );

    public OllamaController(ChatClient chatClient, VectorStore vectorStore) {
        this.chatClient = chatClient;
        this.vectorStore = vectorStore;
    }
     @GetMapping("/chat")
    public Flux<String> chat(@RequestHeader(value = "emailId", required = false) String emailId,
                             @RequestParam String message) {
        log.info("Received chat request: {}", message);
        return chatClientPrompt(message, emailId);
    }

    @GetMapping("/search/chat")
    public Flux<String> searchChat(@RequestHeader(value = "emailId", required = false) String emailId,
                                   @RequestParam String message) {

        if (!isHRMessage(message)) {
            return Flux.just("I’m sorry, this information is not available as per the HR Leave Policy.");
        }

        List<Document> docs = performVectorSearch(message, 5, 0.6);
        if (docs.isEmpty()) {
            log.info("No documents found for message: {}", message);
            return Flux.just("I’m sorry, this information is not available as per the HR Leave Policy.");
        }

        String context = joinDocuments(docs);
        return chatClientPromptWithContext(message, context, emailId);
    }

    // ---------------------- PRIVATE HELPERS ----------------------

    private boolean isHRMessage(String message) {
        return HR_PATTERN.matcher(message).matches();
    }

    private List<Document> performVectorSearch(String query, int topK, double similarityThreshold) {
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> docs = vectorStore.similaritySearch(searchRequest);
        log.info("Vector search for '{}' returned {} documents", query, docs.size());
        return docs;
    }

    private String joinDocuments(List<Document> docs) {
        return docs.stream()
                .map(Document::getText)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private Flux<String> chatClientPrompt(String message, String emailId) {
        var promptSpec = chatClient.prompt()
                .system(hrSystemPromptTemplates)
                .user(message);

        if (emailId != null && !emailId.isEmpty()) {
            promptSpec.advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, emailId));
        }

        return promptSpec.stream().content();
    }

    private Flux<String> chatClientPromptWithContext(String message, String context, String emailId) {
        var promptSpec = chatClient.prompt()
                .system(ps -> ps.text(hrSystemPromptTemplates).param("documents", context))
                .user(message);

        if (emailId != null && !emailId.isEmpty()) {
            promptSpec.advisors(advisorSpec -> advisorSpec.param(ChatMemory.CONVERSATION_ID, emailId));
        }

        return promptSpec.stream().content();
    }

}
