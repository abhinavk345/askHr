package com.intech.ai.caches;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

@Component
public class SemanticResponseCache {

    private final List<SemanticCacheEntry> cache = new CopyOnWriteArrayList<>();

//    public void put(List<Float> embedding, String response) {
//        cache.add(new SemanticCacheEntry(embedding, response));
//    }

    public void put(SemanticCacheEntry entry) {
        cache.add(entry);
    }

    public List<SemanticCacheEntry> getAll() {
        return cache;
    }

    public boolean isEmpty() {
        return cache.isEmpty();
    }
}
