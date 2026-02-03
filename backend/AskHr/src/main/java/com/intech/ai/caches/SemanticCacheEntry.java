package com.intech.ai.caches;

import java.util.List;

public class SemanticCacheEntry {

    private final List<Float> embedding;
    private final String response;

    public SemanticCacheEntry(List<Float> embedding, String response) {
        this.embedding = embedding;
        this.response = response;
    }

    public List<Float> getEmbedding() {
        return embedding;
    }

    public String getResponse() {
        return response;
    }
}
