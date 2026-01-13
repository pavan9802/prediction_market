package com.prediction.market.prediction_market.cache;

import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;

import com.prediction.market.prediction_market.entity.MarketState;
import com.prediction.market.prediction_market.repositories.MarketStateRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class MarketStore {
    private final ConcurrentHashMap<String, MarketState> markets = new ConcurrentHashMap<>();
    private final MarketStateRepository marketStateRepository;

    private static final long IDLE_FLUSH_THRESHOLD_MS = 1000;

    public MarketState getMarketOrCreate(String marketId) {
        return markets.computeIfAbsent(marketId, id -> {
            // Try to load from database
            return marketStateRepository.findByMarketId(id)
                    .orElseGet(() -> {
                        log.warn("Market not found in database: {}. Should be created via admin endpoint.", id);
                        return null; // Markets should be pre-created, not auto-created on trade
                    });
        });
    }

    public MarketState getMarketById(String marketId) {
        return markets.get(marketId);
    }

    public void createMarket(MarketState marketState) {
        markets.put(marketState.getMarketId(), marketState);
        // Persist immediately
        persistMarket(marketState);
    }

    @Scheduled(fixedDelay = 1000)
    public void flushIdleMarkets() {
        long now = System.currentTimeMillis();

        for (MarketState market : markets.values()) {
            if (now - market.getLastTradeTimestamp() > IDLE_FLUSH_THRESHOLD_MS &&
                    market.getLastPersistedTimestamp() < market.getLastTradeTimestamp()) {
                persistMarket(market);
                market.setLastPersistedTimestamp(now);
            }
        }
    }

    @Async
    public void persistMarket(MarketState market) {
        try {
            marketStateRepository.save(market);
            log.debug("Persisted market: {}", market.getMarketId());
        } catch (Exception e) {
            log.error("Failed to persist market: {}", market.getMarketId(), e);
        }
    }
}
