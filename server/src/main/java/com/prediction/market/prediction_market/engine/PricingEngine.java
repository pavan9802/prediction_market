package com.prediction.market.prediction_market.engine;

public class PricingEngine {
 

    // Calculates the current market price of a YES share based on the current shares in the market.
    public double getPrice(double yesShares, double noShares, double liquidityB) {
        double maxQ = Math.max(yesShares, noShares) / liquidityB;
        double expYes = Math.exp((yesShares / liquidityB) - maxQ);
        double expNo = Math.exp((noShares / liquidityB) - maxQ);
        return expYes / (expYes + expNo);
    }

    // Calculates how much it costs for a user to buy a certain number of shares (deltaShares) of an outcome (YES or NO) in the market.
    public double computeCost(double yesShares, double noShares, String outcome, double deltaShares, double liquidityB) {
        double oldCost = cost(yesShares, noShares, liquidityB);
        double newCost;
        if ("YES".equals(outcome)) {
            newCost = cost(yesShares + deltaShares, noShares, liquidityB);
        } else {
            newCost = cost(yesShares, noShares + deltaShares, liquidityB);
        }
        return newCost - oldCost;
    }

    // LMSR cost function
    public double cost(double yesShares, double noShares, double liquidityB) {
        double maxQ = Math.max(yesShares, noShares) / liquidityB;
        return liquidityB * Math.log(
            Math.exp(yesShares / liquidityB - maxQ) + Math.exp(noShares / liquidityB - maxQ)
        ) + liquidityB * maxQ;
    }

}
