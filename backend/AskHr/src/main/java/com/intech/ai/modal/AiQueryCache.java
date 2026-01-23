package com.intech.ai.modal;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Data
@Entity
@Table(name = "ai_query_cache")
public class AiQueryCache {

    @Id
    private String cacheKey;

    @Lob
    private String response;

    private Instant createdAt;
}