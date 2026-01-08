package com.prediction.market.prediction_market.entity;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA
@AllArgsConstructor
@Builder
@Document(collection = "positions")
@CompoundIndex(name = "user_market_idx", def = "{'userId':1,'marketId':1}", unique = true)
public class Position {
    @MongoId
    private String id;
    private String marketId;
    private int yesShares;
    private int noShares;
    private String userId;

    public Position(String marketId) {
        this.marketId = marketId;
    }

    public void addYesShares(int qty) {
        this.yesShares += qty;
    }

    public void addNoShares(int qty) {
        this.noShares += qty;
    }

    public String getMarketId() {
        return marketId;
    }

    public int getYesShares() {
        return yesShares;
    }

    public int getNoShares() {
        return noShares;
    }
}
