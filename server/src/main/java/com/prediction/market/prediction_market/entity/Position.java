package com.prediction.market.prediction_market.entity;
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
public class Position {
    String marketId;
    int yesShares;
    int noShares;

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

