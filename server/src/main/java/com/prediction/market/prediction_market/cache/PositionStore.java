package com.prediction.market.prediction_market.cache;

import java.util.concurrent.ConcurrentHashMap;

import com.prediction.market.prediction_market.entity.Position;
import com.prediction.market.prediction_market.entity.User;

public class PositionStore {
    private final ConcurrentHashMap<String, User> users = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, Position>> positions = new ConcurrentHashMap<>();

    public User getOrCreateUser(String userId) {
        return users.computeIfAbsent(
            userId,
            id -> new User(id, 1000.0) // starter balance
        );
    }

    public void createUser(String userId, double startingBalance) {
        users.putIfAbsent(
            userId,
            new User(userId, startingBalance)
        );
    }

    public Position getOrCreatePosition(String userId, String marketId) {
        return positions
            .computeIfAbsent(userId, id -> new ConcurrentHashMap<>())
            .computeIfAbsent(marketId, id -> new Position(marketId));
    }
}
