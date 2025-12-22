package com.prediction.market.prediction_market.engine;

import com.prediction.market.prediction_market.cache.MarketStore;
import com.prediction.market.prediction_market.cache.PositionStore;
import com.prediction.market.prediction_market.entity.MarketState;
import com.prediction.market.prediction_market.entity.Position;
import com.prediction.market.prediction_market.entity.User;
import com.prediction.market.prediction_market.execution.TradeRequest;


public class MarketEngine {

    
    private final MarketStore marketStore;
    private final PositionStore positionStore;

    private final PricingEngine pricingEngine;
    // Example liquidity parameter
    public MarketEngine(MarketStore marketStore, PositionStore positionStore) {
        this.marketStore = marketStore;
        this.positionStore = positionStore;
        this.pricingEngine = new PricingEngine();
    }

    public double executeTrade(TradeRequest tradeRequest) {
        String outCome = tradeRequest.getOutcome();
        Integer size = tradeRequest.getSize();
        String userId = tradeRequest.getUserId();
        String marketId = tradeRequest.getMarketId();

        MarketState marketState = marketStore.getMarketOrCreate(marketId);
        User user = positionStore.getOrCreateUser(userId);
        Position position = positionStore.getOrCreatePosition(userId, marketId);

        double cost = pricingEngine.computeCost(
        marketState.getYesShares(),
        marketState.getNoShares(),
        outCome,
        size,
        marketState.getLiquidityB()
        );
        //System.out.println("Incoming trade for " + size + " shares of " + outCome + " at cost: " + cost);

        if (!user.hasSufficientBalance(cost)) {
            return 0;
        }
        user.debit(cost);

        if(outCome.equalsIgnoreCase("Yes")){
            position.addYesShares(size);
            marketState.setYesShares(marketState.getYesShares() + size);
        }else{
            position.addNoShares(size);
            marketState.setNoShares(marketState.getNoShares() + size);
        }
        marketState.setLastTradeTimestamp(System.currentTimeMillis());
        marketState.setCurrentPrice(pricingEngine.getPrice(marketState.getYesShares(), marketState.getNoShares(), marketState.getLiquidityB()));
        //System.out.println("Updated Market Price: " + marketState.getCurrentPrice());

        System.out.println();
        return cost;
    }
}
