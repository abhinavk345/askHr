package com.intech.ai.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    public List<Float> embed(String text) {

        // ✅ Your Spring AI returns float[]
        float[] vector = embeddingModel.embed(text);

        // Convert float[] → List<Float>
        List<Float> embedding = new ArrayList<>(vector.length);
        for (float v : vector) {
            embedding.add(v);
        }

        return embedding;
    }
}
