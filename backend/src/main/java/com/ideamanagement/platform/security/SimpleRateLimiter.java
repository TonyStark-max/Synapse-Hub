package com.ideamanagement.platform.security;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

@Component
public class SimpleRateLimiter {

    // Cache to hold timestamps of requests per user per action
    private final ConcurrentHashMap<String, ConcurrentLinkedQueue<Long>> userLogs = new ConcurrentHashMap<>();

    /**
     * Checks if the request is allowed under rate limits.
     * @param userId Unique identifier of the user
     * @param action The endpoint or action name being rate limited
     * @param limitMax Maximum number of requests allowed in the window
     * @param windowMillis Window duration in milliseconds (e.g. 60000 for 1 minute)
     * @return true if the request is allowed, false if rate limit exceeded
     */
    public boolean isAllowed(String userId, String action, int limitMax, long windowMillis) {
        if (userId == null) {
            return true; // Don't block unauthenticated (handled by Spring Security)
        }
        
        String key = userId + ":" + action;
        long now = System.currentTimeMillis();

        userLogs.computeIfAbsent(key, k -> new ConcurrentLinkedQueue<>());
        ConcurrentLinkedQueue<Long> timestamps = userLogs.get(key);

        // Prune expired timestamps outside of the current window
        while (!timestamps.isEmpty() && (now - timestamps.peek() > windowMillis)) {
            timestamps.poll();
        }

        if (timestamps.size() < limitMax) {
            timestamps.add(now);
            return true;
        }

        return false;
    }
}

// Rate limiter token bucket config.
// Rate limiter token bucket config.
// Rate limiter token bucket config.