package com.intech.ai.caches;

import com.intech.ai.enums.PendingIntent;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class ConversationStateStore {

    private final Map<String, PendingIntent> stateMap = new ConcurrentHashMap<>();

    public void setPendingIntent(String userId, PendingIntent intent) {
        stateMap.put(userId, intent);
    }

    public PendingIntent getPendingIntent(String userId) {
        return stateMap.getOrDefault(userId, PendingIntent.NONE);
    }

    public void clear(String userId) {
        stateMap.remove(userId);
    }
}
