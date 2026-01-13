package com.prediction.market.prediction_market.ratelimit;

/**
 * Interface for rate limiting strategies.
 * Implementations can use token bucket, leaky bucket, sliding window, etc.
 */
public interface RateLimiter {

    /**
     * Attempts to acquire a permit for the given identifier.
     *
     * @param identifier The unique identifier for the client (IP, userId, etc.)
     * @return true if the request is allowed, false if rate limit exceeded
     */
    boolean tryAcquire(String identifier);

    /**
     * Gets the time in seconds until the next token is available for the identifier.
     *
     * @param identifier The unique identifier for the client
     * @return Seconds until next token is available (0 if tokens are available)
     */
    long getRetryAfterSeconds(String identifier);

    /**
     * Resets the rate limit for a specific identifier.
     * Useful for testing or administrative purposes.
     *
     * @param identifier The unique identifier to reset
     */
    void reset(String identifier);

    /**
     * Cleans up stale entries to prevent memory leaks.
     * Should be called periodically for in-memory implementations.
     */
    void cleanup();
}
