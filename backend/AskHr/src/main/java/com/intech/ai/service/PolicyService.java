package com.intech.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final AiChatService aiChatService;
    private final PolicySearchService policySearchService;

    /**
     * POLICY RAG with caching
     * IMPORTANT:
     * - Question MUST already be normalized by caller
     * - Cache key = normalized question
     */
    @Cacheable(
            value = "policy-search",
            key = "#normalizedQuestion",
            unless = "#result.contains('Sorry')"
    )
    public String answerPolicy(String normalizedQuestion) {

        // 1️⃣ Vector search (TOP-K kept very small)
        List<Document> documents =
                policySearchService.searchPolicy(normalizedQuestion);

        if (documents == null || documents.isEmpty()) {
            return "Sorry, I could not find relevant HR policy information.";
        }

        // 2️⃣ Build minimal context (critical for latency)
        String context = buildContext(documents);

        // 3️⃣ Lightweight RAG prompt
        String prompt = """
            You are an HR assistant.
            Answer ONLY from the policy context below.
            If the answer is not present, say:
            "Not specified in HR policy."

            Policy Context:
            %s

            Question:
            %s
            """.formatted(context, normalizedQuestion);

        // 4️⃣ Sync LLM call (FASTER than streaming)
        return aiChatService.askSync(prompt);
    }

    /**
     * Keep context extremely small for fast token processing
     */
    private String buildContext(List<Document> docs) {
        return docs.stream()
                .limit(1) // 🔥 TOP-1 is enough for policies
                .map(d ->
                        d.getText().substring(
                                0,
                                Math.min(300, d.getText().length())
                        )
                )
                .collect(Collectors.joining("\n"));
    }
}
