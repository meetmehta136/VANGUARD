package com.vanguard.ingest.service;

import com.vanguard.common.ModelConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class IdempotencyService {

    private final StringRedisTemplate redisTemplate;

    public IdempotencyService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public boolean isAlreadyProcessed(String transactionId) {
        String key = ModelConstants.REDIS_IDEMPOTENCY_PREFIX + transactionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void markProcessed(String transactionId) {
        String key = ModelConstants.REDIS_IDEMPOTENCY_PREFIX + transactionId;
        redisTemplate.opsForValue().set(
            key, "1",
            ModelConstants.REDIS_IDEMPOTENCY_TTL_SEC, TimeUnit.SECONDS
        );
    }
}
