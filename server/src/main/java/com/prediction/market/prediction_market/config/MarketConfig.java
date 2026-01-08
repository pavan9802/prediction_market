package com.prediction.market.prediction_market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.prediction.market.prediction_market.cache.MarketStore;
import com.prediction.market.prediction_market.cache.PositionStore;
import com.prediction.market.prediction_market.engine.MarketEngine;
import com.prediction.market.prediction_market.engine.PricingEngine;

@Configuration
public class MarketConfig {

    @Bean
    public MarketStore marketStore() {
        return new MarketStore();
    }

    @Bean
    public PositionStore positionStore() {
        return new PositionStore();
    }

    @Bean
    PricingEngine pricingEngine() {
        return new PricingEngine();
    }

    @Bean
    public MarketEngine marketEngine(MarketStore marketStore, PositionStore positionStore,
            PricingEngine pricingEngine) {
        return new MarketEngine(marketStore, positionStore, pricingEngine);
    }
}
