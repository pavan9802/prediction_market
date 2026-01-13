package com.prediction.market.prediction_market.ratelimit;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Configuration for API rate limiting.
 *
 * Default configuration:
 * - Capacity: 100 requests (burst capacity)
 * - Refill rate: 10 requests/second (sustained rate)
 * - Exempted paths: /auth/** (login endpoints)
 *
 * This allows authenticated users to make up to 100 requests immediately,
 * then sustained at 10 requests/second (600 requests/minute).
 *
 * For production, consider:
 * - Externalizing configuration to application.properties
 * - Different limits for different user tiers
 * - Persistent storage (Redis) instead of in-memory
 */
@Configuration
@EnableScheduling
public class RateLimitConfig {

    // Rate limit parameters
    private static final int CAPACITY = 100;           // Max burst requests
    private static final double REFILL_RATE = 10.0;   // Requests per second

    // Paths exempted from rate limiting
    private static final List<String> EXEMPTED_PATHS = List.of(
            "/auth/"    // Allow unrestricted access to authentication endpoints
    );

    @Bean
    public RateLimiter rateLimiter() {
        return new TokenBucketRateLimiter(CAPACITY, REFILL_RATE);
    }

    @Bean
    public RateLimitFilter rateLimitFilter(RateLimiter rateLimiter) {
        return new RateLimitFilter(rateLimiter, EXEMPTED_PATHS);
    }

    /**
     * Periodically clean up stale rate limiter entries to prevent memory leaks.
     * Runs every 5 minutes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void cleanupRateLimiter() {
        rateLimiter().cleanup();
    }
}
