package com.vanguard.scoring.service;

import com.vanguard.common.ModelConstants;
import com.vanguard.common.UserFeatures;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.TimeUnit;

@Service
public class RedisFeatureStore {

    private static final Logger log = LoggerFactory.getLogger(RedisFeatureStore.class);

    private final StringRedisTemplate redisTemplate;

    public RedisFeatureStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void storeFeatures(String userId, UserFeatures features) {
        String key = ModelConstants.REDIS_FEATURES_PREFIX + userId;
        redisTemplate.opsForHash().putAll(key, Map.ofEntries(
            Map.entry("txnCount1m", String.valueOf(features.getTxnCount1m())),
            Map.entry("sumAmount1m", String.valueOf(features.getSumAmount1m())),
            Map.entry("txnCount5m", String.valueOf(features.getTxnCount5m())),
            Map.entry("sumAmount5m", String.valueOf(features.getSumAmount5m())),
            Map.entry("uniqueMerchants1hr", String.valueOf(features.getUniqueMerchants1hr())),
            Map.entry("geoDistanceLast", String.valueOf(features.getGeoDistanceLast())),
            Map.entry("timeSinceLastTxnSec", String.valueOf(features.getTimeSinceLastTxnSec())),
            Map.entry("logAmount", String.valueOf(features.getLogAmount())),
            Map.entry("hourSin", String.valueOf(features.getHourSin())),
            Map.entry("hourCos", String.valueOf(features.getHourCos())),
            Map.entry("daySin", String.valueOf(features.getDaySin())),
            Map.entry("dayCos", String.valueOf(features.getDayCos()))
        ));
        redisTemplate.expire(key, ModelConstants.REDIS_FEATURES_TTL_SEC, TimeUnit.SECONDS);
    }

    @CircuitBreaker(name = "redis-feature-store", fallbackMethod = "defaultFeatures")
    public UserFeatures getFeatures(String userId) {
        String key = ModelConstants.REDIS_FEATURES_PREFIX + userId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(key);
        if (entries.isEmpty()) {
            return UserFeatures.defaultValues();
        }
        try {
            return UserFeatures.builder()
                .txnCount1m(toDouble(entries.get("txnCount1m")))
                .sumAmount1m(toDouble(entries.get("sumAmount1m")))
                .txnCount5m(toDouble(entries.get("txnCount5m")))
                .sumAmount5m(toDouble(entries.get("sumAmount5m")))
                .uniqueMerchants1hr(toDouble(entries.get("uniqueMerchants1hr")))
                .geoDistanceLast(toDouble(entries.get("geoDistanceLast")))
                .timeSinceLastTxnSec(toDouble(entries.get("timeSinceLastTxnSec")))
                .logAmount(toDouble(entries.get("logAmount")))
                .hourSin(toDouble(entries.get("hourSin")))
                .hourCos(toDouble(entries.get("hourCos")))
                .daySin(toDouble(entries.get("daySin")))
                .dayCos(toDouble(entries.get("dayCos")))
                .build();
        } catch (Exception e) {
            log.warn("Failed to deserialize features for user {}: {}", userId, e.getMessage());
            return UserFeatures.defaultValues();
        }
    }

    public boolean hasFeatures(String userId) {
        String key = ModelConstants.REDIS_FEATURES_PREFIX + userId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    public void clearFeatures(String userId) {
        String key = ModelConstants.REDIS_FEATURES_PREFIX + userId;
        redisTemplate.delete(key);
    }

    public UserFeatures defaultFeatures(String userId, Exception e) {
        log.warn("Redis CB open for user {}, returning default features: {}", userId, e.getMessage());
        return UserFeatures.defaultValues();
    }

    private static double toDouble(Object value) {
        if (value == null) return 0.0;
        return Double.parseDouble(value.toString());
    }
}
