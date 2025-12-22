package com.prediction.market.prediction_market.engine;

import org.springframework.stereotype.Component;

import com.prediction.market.prediction_market.entity.MarketState;

@Component
public class MarketEngine {

    // private final MarketStore marketStore;
    // private final PositionStore positionStore;
    private final MarketState marketState;
    private final PricingEngine pricingEngine;
    // Example liquidity parameter
    public MarketEngine() {
        this.marketState = MarketState.builder()
        .marketId("1")
        .yesShares(0)  
        .noShares(0)    
        .liquidityB(100)  
        .currentPrice(0.5)  
        .status("OPEN")  
        .lastUpdatedTimestamp(System.currentTimeMillis()).
        build();
        this.pricingEngine = new PricingEngine();
    }

    public void executeTrade(String outCome, Integer size) {
        double cost = pricingEngine.computeCost(
        marketState.getYesShares(),
        marketState.getNoShares(),
        outCome,
        size,
        marketState.getLiquidityB()
        );
        System.out.println("Incoming trade for " + size + " shares of " + outCome + " at cost: " + cost);

        if(outCome.equalsIgnoreCase("Yes")){
            marketState.setYesShares(marketState.getYesShares() + size);
        }else{
            marketState.setNoShares(marketState.getNoShares() + size);
        }
        marketState.setLastUpdatedTimestamp(System.currentTimeMillis());
        marketState.setCurrentPrice(pricingEngine.getPrice(marketState.getYesShares(), marketState.getNoShares(), marketState.getLiquidityB()));
        System.out.println("Updated Market Price: " + marketState.getCurrentPrice());

        System.out.println();

    }
}
