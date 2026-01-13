package com.prediction.market.prediction_market.ratelimit;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Token Bucket rate limiter implementation.
 *
 * Each identifier (IP, userId, etc.) has its own bucket with:
 * - capacity: maximum number of tokens
 * - refillRate: tokens added per second
 * - tokens: current available tokens
 * - lastRefillTime: last time tokens were added
 *
 * Thread-safe using ConcurrentHashMap and synchronized bucket operations.
 */
public class TokenBucketRateLimiter implements RateLimiter {

    private final int capacity;
    private final double refillRate; // tokens per second
    private final Map<String, Bucket> buckets;

    /**
     * Creates a token bucket rate limiter.
     *
     * @param capacity Maximum tokens in the bucket (burst capacity)
     * @param refillRate Tokens added per second (sustained rate)
     */
    public TokenBucketRateLimiter(int capacity, double refillRate) {
        if (capacity <= 0) {
            throw new IllegalArgumentException("Capacity must be positive");
        }
        if (refillRate <= 0) {
            throw new IllegalArgumentException("Refill rate must be positive");
        }
        this.capacity = capacity;
        this.refillRate = refillRate;
        this.buckets = new ConcurrentHashMap<>();
    }

    @Override
    public boolean tryAcquire(String identifier) {
        Bucket bucket = buckets.computeIfAbsent(identifier, k -> new Bucket(capacity));
        return bucket.tryConsume();
    }

    @Override
    public long getRetryAfterSeconds(String identifier) {
        Bucket bucket = buckets.get(identifier);
        if (bucket == null || bucket.getTokens() >= 1.0) {
            return 0;
        }
        // Calculate time needed to refill at least 1 token
        double tokensNeeded = 1.0 - bucket.getTokens();
        return (long) Math.ceil(tokensNeeded / refillRate);
    }

    @Override
    public void reset(String identifier) {
        buckets.remove(identifier);
    }

    @Override
    public void cleanup() {
        // Remove buckets that are full and haven't been used recently (>5 minutes)
        long fiveMinutesAgo = Instant.now().getEpochSecond() - 300;
        buckets.entrySet().removeIf(entry -> {
            Bucket bucket = entry.getValue();
            return bucket.isFull() && bucket.getLastRefillTime() < fiveMinutesAgo;
        });
    }

    /**
     * Internal bucket state for a single identifier.
     * Not thread-safe on its own - synchronization handled by methods.
     */
    private class Bucket {
        private double tokens;
        private long lastRefillTime; // epoch seconds

        Bucket(int capacity) {
            this.tokens = capacity;
            this.lastRefillTime = Instant.now().getEpochSecond();
        }

        synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        synchronized double getTokens() {
            refill();
            return tokens;
        }

        synchronized boolean isFull() {
            refill();
            return tokens >= capacity;
        }

        synchronized long getLastRefillTime() {
            return lastRefillTime;
        }

        /**
         * Refills tokens based on elapsed time since last refill.
         * Called before every operation to ensure accurate state.
         */
        private void refill() {
            long now = Instant.now().getEpochSecond();
            long elapsedSeconds = now - lastRefillTime;

            if (elapsedSeconds > 0) {
                double tokensToAdd = elapsedSeconds * refillRate;
                tokens = Math.min(capacity, tokens + tokensToAdd);
                lastRefillTime = now;
            }
        }
    }
}
