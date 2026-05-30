package com.vanguard.ingest.ratelimit;

import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Map;

@Aspect
@Component
public class RateLimitAspect {

    private static final Logger log = LoggerFactory.getLogger(RateLimitAspect.class);

    private final RateLimiterService rateLimiterService;
    private final MeterRegistry meterRegistry;

    public RateLimitAspect(RateLimiterService rateLimiterService, MeterRegistry meterRegistry) {
        this.rateLimiterService = rateLimiterService;
        this.meterRegistry = meterRegistry;
    }

    @Around("@annotation(rateLimited)")
    public Object enforceRateLimit(ProceedingJoinPoint joinPoint, RateLimited rateLimited) throws Throwable {
        String userId = resolveUserId();
        if (userId == null) {
            return joinPoint.proceed();
        }

        String redisKey = "ratelimit:user:" + userId;
        boolean allowed = rateLimiterService.tryAcquire(
            redisKey, rateLimited.capacity(), rateLimited.refillPerMinute());

        if (!allowed) {
            log.warn("Rate limited user: {}", userId);
            meterRegistry.counter("ratelimit.requests.rejected",
                "userId", userId).increment();
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .body(Map.of("error", "rate_limit_exceeded",
                    "message", "Too many requests. Please try again later.",
                    "retryAfterSeconds", 60 / rateLimited.refillPerMinute()));
        }

        return joinPoint.proceed();
    }

    private String resolveUserId() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs == null) return null;
        HttpServletRequest request = attrs.getRequest();
        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            userId = request.getParameter("userId");
        }
        return userId;
    }
}
