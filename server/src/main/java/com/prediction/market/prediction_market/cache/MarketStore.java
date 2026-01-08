package com.prediction.market.prediction_market.cache;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import com.prediction.market.prediction_market.entity.MarketState;

@EnableScheduling
public class MarketStore {
    private ConcurrentHashMap<String, MarketState> markets = new ConcurrentHashMap<>();
    private final long IDLE_FLUSH_THRESHOLD_MS = 1000;

    public MarketState getMarketOrCreate(String marketId) {
        return markets.computeIfAbsent(marketId, id -> {
            // load from DB
            return null;
        });

    }

    public MarketState getMarketById(String marketId) {
        return markets.get(marketId);
    }

    public void createMarket(MarketState marketState) {
        markets.put(marketState.getMarketId(), marketState);
    }

    @Scheduled(fixedDelay = 1000)
    public void flushIdleMarkets() {
        long now = System.currentTimeMillis();

        for (MarketState market : markets.values()) {
            if (now - market.getLastTradeTimestamp() > IDLE_FLUSH_THRESHOLD_MS &&
                    market.getLastPersistedTimestamp() < market.getLastTradeTimestamp()) {
                // Async persistMarket(market);
                market.setLastPersistedTimestamp(now);
            }
        }
    }

}
