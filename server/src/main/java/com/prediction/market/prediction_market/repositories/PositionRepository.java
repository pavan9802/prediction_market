package com.prediction.market.prediction_market.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.prediction.market.prediction_market.entity.Position;

import java.util.Optional;

@Repository
public interface PositionRepository extends MongoRepository<Position, String> {
    Optional<Position> findByUserIdAndMarketId(String userId, String marketId);
}
