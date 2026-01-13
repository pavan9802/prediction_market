package com.prediction.market.prediction_market.security;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;

public class RateLimiterService {

    private final Map<String, RateLimiter> cache = new ConcurrentHashMap<>();

    public boolean allowRequest(String key) {
        RateLimiter rateLimiter = cache.computeIfAbsent(key, k -> RateLimiter.of(
                RateLimiterConfig.custom()
                        .limitRefreshPeriod(Duration.ofSeconds(1))
                        .limitForPeriod(10) // 10 req/sec per user
                        .build()));
        return rateLimiter.acquirePermission();
    }
}
