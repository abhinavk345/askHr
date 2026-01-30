package com.intech.ai.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PolicySearchService {

    private final VectorStore vectorStore;

    @Cacheable(value = "policy-search", key = "#message.toLowerCase().trim()")
    public List<Document> searchPolicy(String message) {
        return vectorStore.similaritySearch(message);
    }
}