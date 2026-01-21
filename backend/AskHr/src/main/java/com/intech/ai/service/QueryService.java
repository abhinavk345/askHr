package com.intech.ai.service;

import com.intech.ai.utility.HRUtility;
import com.intech.ai.utility.IntentDetector;
import lombok.RequiredArgsConstructor;
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

@Service
@RequiredArgsConstructor
public class QueryService {

    private final VectorStore vectorStore;
    private final AiChatService aiChatService;

    // Cache with TTL
    private final Map<String, CachedFlux> queryCache = new ConcurrentHashMap<>();
    private final Duration ttl = Duration.ofMinutes(15); // e.g., cache expires after 15 minutes

    /**
     * Handles HR policy related queries with streaming support and optional caching.
     */
    public Flux<String> handlePolicyQuery(String message) {

        // Fast bypass for non-policy messages
        if (!IntentDetector.isPolicyQuery(message)) {
            return Flux.just(
                    "Hello! I can answer HR leave policy related questions. " +
                            "Please ask about leave, holidays, or HR policies."
            );
        }

        // Check cache
        CachedFlux cached = queryCache.get(message);
        if (cached != null && !cached.isExpired()) {
            return cached.flux();
        }

        // Fetch new Flux and cache it
        Flux<String> flux = fetchPolicyFlux(message);
        queryCache.put(message, new CachedFlux(flux, Instant.now(), ttl));
        return flux;
    }

    public Flux<String> handlePolicyQuery(String message, String userId) {

        // 1️⃣ Non-policy messages
        if (!IntentDetector.isPolicyQuery(message)) {
            return Flux.just(
                    "Hi! I specialize in HR leave policies and holidays. " +
                            "You can ask me things like 'Explain leave policy', 'What holidays are coming?', or 'How do I apply for leave?'."
            );
        }

        // 2️⃣ Check cache
        CachedFlux cached = queryCache.get(message);
        if (cached != null && !cached.isExpired()) {
            return cached.flux();
        }

        // 3️⃣ Fetch documents from vector store
        Flux<String> flux = Flux.defer(() -> {
            List<Document> documents = vectorStore.similaritySearch(message);

            if (documents.isEmpty()) {
                return Flux.just(
                        "I couldn’t find specific info for your query in the HR policy documents. " +
                                "You can still check the portal or contact HR for clarification."
                );
            }

            // Combine context
            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            String prompt = """
            You are a friendly and helpful HR assistant.
            Answer strictly using the HR policy context below.
            Use bullet points if applicable.
            If some information is missing, suggest alternative options.
            
            Context:
            %s
            
            User question:
            %s
            """.formatted(context, message);

            return aiChatService.askStream(prompt)
                    .map(s -> s.replaceAll("\\n+", "\n")) // clean extra newlines
                    .startWith("Let me check that for you..."); // more human-like
        }).cache();

        // 4️⃣ Store in cache
        queryCache.put(message, new CachedFlux(flux, Instant.now(), ttl));
        return flux;
    }

    /**
     * Internal method that does the actual vector search and AI call,
     * returns a cached Flux for streaming.
     */
    private Flux<String> fetchPolicyFlux(String message) {
        try {
            List<Document> documents = vectorStore.similaritySearch(message);

            if (documents.isEmpty()) {
                return Flux.just(
                        "I’m sorry, this information is not available as per the HR Leave Policy."
                );
            }

            String context = documents.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n"));

            String prompt = """
                    You are an HR assistant.
                    Answer strictly using the HR policy context below.
                    If the answer is not present, say "I’m sorry, this information is not available as per the HR Leave Policy."

                    Context:
                    %s

                    Question:
                    %s
                    """.formatted(context, message);

            return aiChatService.askStream(prompt)
                    .cache(); // ensures Flux is reusable for multiple subscribers

        } catch (Exception ex) {
            return Flux.just(
                    "Sorry, I am unable to process your request at the moment. Please try again later."
            );
        }
    }

    /**
     * Holder class for cached Flux with timestamp
     */
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
