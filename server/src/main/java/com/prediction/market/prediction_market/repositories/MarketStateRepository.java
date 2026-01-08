package com.prediction.market.prediction_market.repositories;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.prediction.market.prediction_market.entity.MarketState;

@Repository
public interface MarketStateRepository extends MongoRepository<MarketState, String> {
    Optional<MarketState> findByMarketId(String marketId);

}
