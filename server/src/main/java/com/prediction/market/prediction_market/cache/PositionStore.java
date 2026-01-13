package com.prediction.market.prediction_market.cache;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import com.prediction.market.prediction_market.entity.Position;
import com.prediction.market.prediction_market.entity.User;
import com.prediction.market.prediction_market.repositories.PositionRepository;
import com.prediction.market.prediction_market.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class PositionStore {
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Position>> positions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> userLastModified = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Long> positionLastModified = new ConcurrentHashMap<>();

    private final UserRepository userRepository;
    private final PositionRepository positionRepository;

    private static final long IDLE_FLUSH_THRESHOLD_MS = 1000;
    private static final double DEFAULT_STARTING_BALANCE = 10000.0;

    public User getOrCreateUser(String userId) {
        return users.computeIfAbsent(userId, id -> {
            // Try to load from database
            return userRepository.findByUserId(id)
                    .orElseGet(() -> {
                        // Create new user with default balance
                        log.info("Creating new user in cache: {}", id);
                        User newUser = User.builder()
                                .userId(id)
                                .balance(DEFAULT_STARTING_BALANCE)
                                .build();
                        // Persist immediately (async)
                        persistUser(newUser);
                        return newUser;
                    });
        });
    }

    public Position getOrCreatePosition(String userId, String marketId) {
        return positions
                .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
                .computeIfAbsent(marketId, mktId -> {
                    // Try to load from database
                    return positionRepository.findByUserIdAndMarketId(userId, mktId)
                            .orElseGet(() -> {
                                log.info("Creating new position in cache: user={}, market={}", userId, mktId);
                                return Position.builder()
                                        .userId(userId)
                                        .marketId(mktId)
                                        .yesShares(0)
                                        .noShares(0)
                                        .build();
                            });
                });
    }

    public void markUserModified(String userId) {
        userLastModified.put(userId, System.currentTimeMillis());
    }

    public void markPositionModified(String userId, String marketId) {
        String key = userId + ":" + marketId;
        positionLastModified.put(key, System.currentTimeMillis());
    }

    @Scheduled(fixedDelay = 1000)
    public void flushIdlePositionsAndUsers() {
        long now = System.currentTimeMillis();

        // Flush idle users
        for (var entry : userLastModified.entrySet()) {
            String userId = entry.getKey();
            long lastModified = entry.getValue();

            if (now - lastModified > IDLE_FLUSH_THRESHOLD_MS) {
                User user = users.get(userId);
                if (user != null) {
                    persistUser(user);
                    userLastModified.remove(userId);
                }
            }
        }

        // Flush idle positions
        for (var entry : positionLastModified.entrySet()) {
            String key = entry.getKey();
            long lastModified = entry.getValue();

            if (now - lastModified > IDLE_FLUSH_THRESHOLD_MS) {
                String[] parts = key.split(":");
                if (parts.length == 2) {
                    String userId = parts[0];
                    String marketId = parts[1];

                    ConcurrentHashMap<String, Position> userPositions = positions.get(userId);
                    if (userPositions != null) {
                        Position position = userPositions.get(marketId);
                        if (position != null) {
                            persistPosition(position);
                            positionLastModified.remove(key);
                        }
                    }
                }
            }
        }
    }

    @Async
    public void persistUser(User user) {
        try {
            userRepository.save(user);
            log.debug("Persisted user: {}", user.getUserId());
        } catch (Exception e) {
            log.error("Failed to persist user: {}", user.getUserId(), e);
        }
    }

    @Async
    public void persistPosition(Position position) {
        try {
            positionRepository.save(position);
            log.debug("Persisted position: user={}, market={}", position.getUserId(), position.getMarketId());
        } catch (Exception e) {
            log.error("Failed to persist position: user={}, market={}",
                    position.getUserId(), position.getMarketId(), e);
        }
    }
}
