package com.intech.ai.repository;

import com.intech.ai.modal.AiQueryCache;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AiQueryCacheRepository
        extends JpaRepository<AiQueryCache, String> {
}