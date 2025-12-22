package com.prediction.market.prediction_market.execution;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import com.prediction.market.prediction_market.engine.MarketEngine;

public class MarketExecutor {
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final MarketEngine marketEngine;

    public MarketExecutor(MarketEngine marketEngine) {
        this.marketEngine = marketEngine;
    }

    public void submit(TradeRequest request) {
        executor.submit(() -> marketEngine.executeTrade(
            request
        ));
    }
}
