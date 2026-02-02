package com.intech.ai.service;import com.intech.ai.modal.AiQueryCache;

import com.intech.ai.repository.AiQueryCacheRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class QueryService {

    private final VectorStore vectorStore;
    private final AiChatService aiChatService;
    private final AiQueryCacheRepository cacheRepository;

    private final Duration ttl = Duration.ofMinutes(15);

    public QueryService(VectorStore vectorStore, AiChatService aiChatService, AiQueryCacheRepository cacheRepository) {
        this.vectorStore = vectorStore;
        this.aiChatService = aiChatService;
        this.cacheRepository = cacheRepository;
    }

    /**
     * Main method for handling HR policy queries.
     */
    public Flux<String> handlePolicyQuery(String message, String userId) {

        String cacheKey = buildCacheKey(message, userId);

        // 1️⃣ Check DB cache
        return cacheRepository.findById(cacheKey)
                .filter(cache -> !isExpired(cache))
                .map(cache -> {
                    log.info("Serving response from DB cache: {}", cacheKey);
                    return Flux.just(cache.getResponse())
                            .delayElements(Duration.ofMillis(30));
                })
                .orElseGet(() -> generateAndCacheResponse(message, userId, cacheKey));
    }

    /* ================= CORE LOGIC ================= */

    private Flux<String> generateAndCacheResponse(
            String message,
            String userId,
            String cacheKey
    ) {
        return Flux.defer(() -> {

            List<Document> documents = vectorStore.similaritySearch(message);

            if (documents.isEmpty()) {
                return Flux.just(
                        "I couldn’t find specific info for your query in the HR policy documents. " +
                                "Please check the HR portal or contact HR."
                );
            }

            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            String prompt = buildPrompt(message, userId, context);

            StringBuilder finalAnswer = new StringBuilder();

            return aiChatService.askStream(prompt)
                    .map(chunk -> {
                        finalAnswer.append(chunk);
                        return chunk.replaceAll("\\n+", "\n");
                    })
                    .startWith("Let me check that for you...\n")
                    .doOnComplete(() -> saveToCache(cacheKey, finalAnswer.toString()));

        });
    }

    /* ================= CACHE ================= */

    private void saveToCache(String cacheKey, String response) {
        AiQueryCache cache = new AiQueryCache();
        cache.setCacheKey(cacheKey);
        cache.setResponse(response);
        cache.setCreatedAt(Instant.now());

        cacheRepository.save(cache);
        log.info("Saved response to DB cache: {}", cacheKey);
    }

    private boolean isExpired(AiQueryCache cache) {
        return Instant.now().isAfter(cache.getCreatedAt().plus(ttl));
    }

    /* ================= HELPERS ================= */

    private String buildPrompt(String message, String userId, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a friendly and helpful HR assistant.\n");
        sb.append("Answer strictly using the HR policy context below.\n");
        sb.append("Use bullet points if applicable.\n");
        sb.append("If some information is missing, suggest alternatives.\n\n");

        if (userId != null && !userId.isBlank()) {
            sb.append("User ID: ").append(userId).append("\n");
        }

        sb.append("Context:\n").append(context).append("\n\n");
        sb.append("User question:\n").append(message).append("\nAnswer:");
        return sb.toString();
    }

    private String buildCacheKey(String message, String userId) {
        return message.toLowerCase().trim() + "::" +
                (userId != null ? userId : "anon");
    }
}
