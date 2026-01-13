package com.prediction.market.prediction_market.ratelimit;

/**
 * Exception thrown when a client exceeds their rate limit.
 * This is a runtime exception that will be caught by the filter chain.
 */
public class RateLimitExceededException extends RuntimeException {

    private final String identifier;
    private final long retryAfterSeconds;

    public RateLimitExceededException(String identifier, long retryAfterSeconds) {
        super(String.format("Rate limit exceeded for %s. Retry after %d seconds.", identifier, retryAfterSeconds));
        this.identifier = identifier;
        this.retryAfterSeconds = retryAfterSeconds;
    }

    public String getIdentifier() {
        return identifier;
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
