package com.vanguard.limiter.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;

@Service
public class RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterService.class);

    private static final String LUA_SCRIPT = """
        local key = KEYS[1]
        local capacity = tonumber(ARGV[1])
        local refillRate = tonumber(ARGV[2])
        local now = tonumber(ARGV[3])

        local bucket = redis.call('hmget', key, 'tokens', 'lastRefill')
        local tokens = tonumber(bucket[1]) or capacity
        local lastRefill = tonumber(bucket[2]) or now

        local elapsed = math.max(0, now - lastRefill)
        local refill = math.floor(elapsed * refillRate / 60)

        tokens = math.min(capacity, tokens + refill)

        if tokens >= 1 then
            tokens = tokens - 1
            redis.call('hmset', key, 'tokens', tokens, 'lastRefill', now)
            redis.call('expire', key, 3600)
            return {1, tokens}
        else
            redis.call('hmset', key, 'tokens', tokens, 'lastRefill', lastRefill)
            redis.call('expire', key, 3600)
            return {0, tokens}
        end
        """;

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> rateLimitScript;

    public RateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.rateLimitScript = RedisScript.of(LUA_SCRIPT, List.class);
        log.info("RateLimiter initialized — token bucket Lua script loaded");
    }

    public boolean tryAcquire(String key, int capacity, int refillPerMinute) {
        long now = Instant.now().getEpochSecond();
        List<Long> result = redisTemplate.execute(
            rateLimitScript,
            List.of(key),
            String.valueOf(capacity),
            String.valueOf(refillPerMinute),
            String.valueOf(now)
        );
        boolean allowed = result != null && result.get(0) == 1L;
        if (!allowed) {
            log.warn("Rate limit exceeded for key: {}", key);
        }
        return allowed;
    }

    public int getRemainingTokens(String key, int capacity) {
        String tokensStr = (String) redisTemplate.opsForHash().get(key, "tokens");
        if (tokensStr == null) return capacity;
        return Integer.parseInt(tokensStr);
    }
}
