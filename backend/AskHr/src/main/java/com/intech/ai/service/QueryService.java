package com.intech.ai.service;

import com.intech.ai.utility.IntentDetector;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QueryService {

    private final VectorStore vectorStore;
    private final AiChatService aiChatService;

    // Cache with TTL
    private final Map<String, CachedFlux> queryCache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(15);

    /**
     * Main method for handling HR policy queries.
     * Streams responses and caches results for repeated queries.
     */
    public Flux<String> handlePolicyQuery(String message, String userId) {
//        if (!IntentDetector.isPolicyQuery(message)) {
//            return Flux.just(
//                    "Hi! I specialize in HR leave policies and holidays. " +
//                            "You can ask me things like 'Explain leave policy', 'What holidays are coming?', or 'How do I apply for leave?'."
//            );
//        }

        String cacheKey = buildCacheKey(message, userId);
        CachedFlux cached = queryCache.get(cacheKey);

        if (cached != null && !cached.isExpired()) {
            log.info("Serving cached response for key: {}", cacheKey);
            return cached.flux().delayElements(Duration.ofMillis(30));
        }

        Flux<String> flux = Flux.defer(() -> {
            List<Document> documents = vectorStore.similaritySearch(message);

            if (documents.isEmpty()) {
                return Flux.just(
                        "I couldn’t find specific info for your query in the HR policy documents. " +
                                "You can still check the portal or contact HR for clarification."
                );
            }

            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            String prompt = buildPrompt(message, userId, context);

            return aiChatService.askStream(prompt)
                    .map(s -> s.replaceAll("\\n+", "\n"))
                    .startWith("Let me check that for you...");

        }).cache(); // ensures multiple subscribers share the same Flux

        queryCache.put(cacheKey, new CachedFlux(flux, Instant.now(), ttl));
        return flux;
    }

    /* ================= PRIVATE HELPERS ================= */

    private String buildPrompt(String message, String userId, String context) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a friendly and helpful HR assistant.\n");
        sb.append("Answer strictly using the HR policy context below.\n");
        sb.append("Use bullet points if applicable.\n");
        sb.append("If some information is missing, suggest alternative options.\n\n");

        if (userId != null && !userId.isBlank()) {
            sb.append("User ID: ").append(userId).append("\n");
        }

        sb.append("Context:\n").append(context).append("\n\n");
        sb.append("User question:\n").append(message).append("\nAnswer:");
        return sb.toString();
    }

    private String buildCacheKey(String message, String userId) {
        return message + "::" + (userId != null ? userId : "anon");
    }

    /* ================= CACHED FLUX HOLDER ================= */

    private static class CachedFlux {
        private final Flux<String> flux;
        private final Instant createdAt;
        private final Duration ttl;

        public CachedFlux(Flux<String> flux, Instant createdAt, Duration ttl) {
            this.flux = flux;
            this.createdAt = createdAt;
            this.ttl = ttl;
        }

        public boolean isExpired() {
            return Instant.now().isAfter(createdAt.plus(ttl));
        }

        public Flux<String> flux() {
            return flux;
        }
    }
}
