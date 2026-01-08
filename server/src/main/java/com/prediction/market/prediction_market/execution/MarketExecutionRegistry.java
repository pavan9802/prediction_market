package com.prediction.market.prediction_market.execution;

import java.util.concurrent.ConcurrentHashMap;

import com.prediction.market.prediction_market.engine.MarketEngine;
import com.prediction.market.prediction_market.entity.TradeRequest;

public class MarketExecutionRegistry {
    private final ConcurrentHashMap<String, MarketExecutor> executors = new ConcurrentHashMap<>();
    private final MarketEngine marketEngine;

    public MarketExecutionRegistry(MarketEngine marketEngine) {
        this.marketEngine = marketEngine;
    }

    public void submitTrade(TradeRequest request) {
        executors
                .computeIfAbsent(
                        request.getMarketId(),
                        id -> new MarketExecutor(marketEngine))
                .submit(request);
    }
}
