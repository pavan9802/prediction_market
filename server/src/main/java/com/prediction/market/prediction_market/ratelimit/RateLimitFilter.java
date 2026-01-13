package com.prediction.market.prediction_market.ratelimit;

import java.io.IOException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet filter that enforces rate limits on incoming HTTP requests.
 *
 * Rate limiting strategy:
 * 1. Authenticated users: rate limit by userId (from JWT token)
 * 2. Unauthenticated users: rate limit by IP address
 * 3. Exempted paths: /auth/**, /actuator/** (configurable)
 *
 * When rate limit is exceeded:
 * - Returns HTTP 429 Too Many Requests
 * - Sets Retry-After header with seconds to wait
 * - Sets X-RateLimit-* headers for client visibility
 */
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final List<String> exemptedPaths;

    public RateLimitFilter(RateLimiter rateLimiter, List<String> exemptedPaths) {
        this.rateLimiter = rateLimiter;
        this.exemptedPaths = exemptedPaths != null ? exemptedPaths : List.of();
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String requestPath = request.getRequestURI();

        // Skip rate limiting for exempted paths
        if (isExempted(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Determine identifier: userId (if authenticated) or IP address
        String identifier = getIdentifier(request);

        // Try to acquire rate limit permit
        boolean allowed = rateLimiter.tryAcquire(identifier);

        if (!allowed) {
            // Rate limit exceeded - return 429
            long retryAfter = rateLimiter.getRetryAfterSeconds(identifier);
            sendRateLimitExceededResponse(response, identifier, retryAfter);
            return;
        }

        // Add rate limit headers for client visibility
        addRateLimitHeaders(response, identifier);

        // Continue filter chain
        filterChain.doFilter(request, response);
    }

    /**
     * Determines the unique identifier for rate limiting.
     * Prefers userId from authentication, falls back to IP address.
     */
    private String getIdentifier(HttpServletRequest request) {
        // Try to get userId from Spring Security context
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && auth.getPrincipal() instanceof String) {
            String userId = (String) auth.getPrincipal();
            if (userId != null && !userId.equals("anonymousUser")) {
                return "user:" + userId;
            }
        }

        // Fall back to IP address
        String clientIp = getClientIp(request);
        return "ip:" + clientIp;
    }

    /**
     * Extracts client IP address, handling X-Forwarded-For header.
     */
    private String getClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            // Take the first IP in the chain
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Checks if the request path is exempted from rate limiting.
     */
    private boolean isExempted(String path) {
        for (String exemptedPath : exemptedPaths) {
            if (path.startsWith(exemptedPath)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Sends a 429 Too Many Requests response with appropriate headers.
     */
    private void sendRateLimitExceededResponse(
            HttpServletResponse response,
            String identifier,
            long retryAfter) throws IOException {

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader("Retry-After", String.valueOf(retryAfter));
        response.setHeader("X-RateLimit-Limit", "Rate limit exceeded");
        response.setHeader("X-RateLimit-Identifier", identifier);
        response.setContentType("application/json");

        String jsonResponse = String.format(
                "{\"error\":\"Rate limit exceeded\",\"identifier\":\"%s\",\"retryAfter\":%d}",
                identifier, retryAfter);
        response.getWriter().write(jsonResponse);
        response.getWriter().flush();
    }

    /**
     * Adds informational rate limit headers to successful responses.
     */
    private void addRateLimitHeaders(HttpServletResponse response, String identifier) {
        response.setHeader("X-RateLimit-Identifier", identifier);
    }
}
