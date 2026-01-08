package com.prediction.market.prediction_market.config;

import org.springframework.context.annotation.Configuration;

import com.prediction.market.prediction_market.engine.MarketEngine;
import com.prediction.market.prediction_market.execution.MarketExecutionRegistry;

@Configuration
public class RegistryConfig {
    public MarketExecutionRegistry marketExecutionRegistry(MarketEngine marketEngine) {
        return new MarketExecutionRegistry(marketEngine);
    }
}
