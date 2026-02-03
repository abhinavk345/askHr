package com.intech.ai.caches;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class SemanticResponseCache {

    private final List<SemanticCacheEntry> cache = new ArrayList<>();

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
