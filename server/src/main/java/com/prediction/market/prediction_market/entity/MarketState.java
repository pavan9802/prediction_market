package com.prediction.market.prediction_market.entity;

import org.springframework.data.mongodb.core.mapping.Document;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

//@
@Document(collection = "markets")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA
@AllArgsConstructor
@Builder
public class MarketState {
    private String marketId;
    private double yesShares;
    private double noShares;
    private double liquidityB;
    private double currentPrice;
    private String status; // OPEN, RESOLVED, etc.
    long lastTradeTimestamp; // updated by MarketEngine
    long lastPersistedTimestamp; // updated by MarketStore
}
