package com.prediction.market.prediction_market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.prediction.market.prediction_market.cache.MarketStore;
import com.prediction.market.prediction_market.cache.PositionStore;
import com.prediction.market.prediction_market.engine.MarketEngine;
import com.prediction.market.prediction_market.engine.PricingEngine;
import com.prediction.market.prediction_market.repositories.MarketStateRepository;
import com.prediction.market.prediction_market.repositories.PositionRepository;
import com.prediction.market.prediction_market.repositories.TransactionRepository;
import com.prediction.market.prediction_market.repositories.UserRepository;
import com.prediction.market.prediction_market.service.BalanceService;
import com.prediction.market.prediction_market.service.OrderExecutionService;

@Configuration
public class MarketConfig {

    @Bean
    public MarketStore marketStore(MarketStateRepository marketStateRepository) {
        return new MarketStore(marketStateRepository);
    }

    @Bean
    public PositionStore positionStore(UserRepository userRepository, PositionRepository positionRepository) {
        return new PositionStore(userRepository, positionRepository);
    }

    @Bean
    PricingEngine pricingEngine() {
        return new PricingEngine();
    }

    @Bean
    public MarketEngine marketEngine(OrderExecutionService orderExecutionService) {
        return new MarketEngine(orderExecutionService);
    }
}
