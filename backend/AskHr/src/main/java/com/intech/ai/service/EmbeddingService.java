package com.intech.ai.service;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    private final Map<String, List<Float>> embeddingCache = new ConcurrentHashMap<>();

    public List<Float> embed(String text) {
        return embeddingCache.computeIfAbsent(text, t -> {
            float[] vector = embeddingModel.embed(t);
            List<Float> list = new ArrayList<>(vector.length);
            for (float v : vector) list.add(v);
            return list;
        });
    }
}
