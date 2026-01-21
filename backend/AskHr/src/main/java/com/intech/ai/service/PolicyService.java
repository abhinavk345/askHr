package com.intech.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PolicyService {

    private final VectorStore vectorStore;
    private final AiChatService aiChatService;

    public String answerPolicy(String question) throws Exception {

        // 1️⃣ Retrieve TOP 3 relevant policy chunks
        List<Document> documents = vectorStore.similaritySearch(question);

        if (documents.isEmpty()) {
            return "Sorry, I could not find relevant HR policy information.";
        }

        // 2️⃣ Build context
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n"));

        // 3️⃣ RAG prompt
        String prompt = """
                You are an HR assistant.
                Answer strictly using the HR policy context below.
                If the answer is not present, say "Not specified in HR policy".

                Context:
                %s

                Question:
                %s
                """.formatted(context, question);

        // 4️⃣ LLM call
        return aiChatService.ask(prompt).get();
    }
}
