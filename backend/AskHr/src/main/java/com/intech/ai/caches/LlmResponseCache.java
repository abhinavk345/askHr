package com.intech.ai.caches;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class LlmResponseCache {

    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String get(String key) {
        return cache.get(key);
    }

    public void put(String key, String response) {
        cache.put(key, response);
    }

    public boolean contains(String key) {
        return cache.containsKey(key);
    }
}
