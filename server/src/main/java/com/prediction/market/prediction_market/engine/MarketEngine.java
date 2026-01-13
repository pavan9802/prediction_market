package com.prediction.market.prediction_market.engine;

import com.prediction.market.prediction_market.entity.Order;
import com.prediction.market.prediction_market.entity.TradeRequest;
import com.prediction.market.prediction_market.service.OrderExecutionService;

import lombok.extern.slf4j.Slf4j;

/**
 * MarketEngine - Coordinator for per-market trade execution.
 *
 * ARCHITECTURE:
 * This class is called by MarketExecutor (per-market single thread)
 * to ensure deterministic ordering of trades within each market.
 *
 * Per-Market Thread Isolation:
 * - Each market has its own ExecutorService (single thread)
 * - Trades for market A don't block trades for market B
 * - Within a market, trades execute sequentially (deterministic)
 *
 * Delegation:
 * MarketEngine delegates to OrderExecutionService which handles:
 * - Order creation and validation
 * - State machine management
 * - Ledger append
 * - Balance updates
 * - Position updates
 *
 * Flow:
 * TradeRequest → MarketExecutionRegistry
 *               → MarketExecutor (per-market thread)
 *               → MarketEngine.executeTrade()
 *               → OrderExecutionService.executeMarketOrder()
 *
 * BENEFITS:
 * 1. Per-market thread isolation ensures deterministic trade ordering
 * 2. OrderExecutionService provides full order lifecycle management
 * 3. Clean separation: MarketEngine = coordination, OrderExecutionService = execution
 * 4. Maintains all Tier 1 guarantees (atomicity, idempotency, validation, etc.)
 */
@Slf4j
public class MarketEngine {

    private final OrderExecutionService orderExecutionService;

    public MarketEngine(OrderExecutionService orderExecutionService) {
        this.orderExecutionService = orderExecutionService;
    }

    /**
     * Execute a trade request.
     *
     * This method runs on a per-market single-threaded executor,
     * ensuring deterministic ordering of trades within each market.
     *
     * @param tradeRequest the trade request
     * @return cost of the trade (0 if rejected)
     */
    public double executeTrade(TradeRequest tradeRequest) {
        String userId = tradeRequest.getUserId();
        String marketId = tradeRequest.getMarketId();
        String outcome = tradeRequest.getOutcome();
        int quantity = tradeRequest.getSize();

        // Extract nonce from request (if provided) for idempotency
        String nonce = tradeRequest.getNonce();

        try {
            // Delegate to OrderExecutionService for full order lifecycle
            Order order = orderExecutionService.executeMarketOrder(
                    userId,
                    marketId,
                    outcome,
                    quantity,
                    nonce
            );

            // Return total cost (0 if order was rejected)
            if (order.getTotalCost() != null) {
                double cost = order.getTotalCost().doubleValue();
                log.debug("Trade executed via order: orderId={}, cost={}", order.getId(), cost);
                return cost;
            } else {
                log.warn("Trade rejected: orderId={}, status={}, reason={}",
                        order.getId(), order.getStatus(), order.getRejectionReason());
                return 0;
            }

        } catch (IllegalArgumentException e) {
            // Validation failed or order rejected
            log.warn("Trade validation failed: userId={}, marketId={}, error={}",
                    userId, marketId, e.getMessage());
            return 0;

        } catch (Exception e) {
            // Unexpected error
            log.error("Trade execution failed: userId={}, marketId={}, error={}",
                    userId, marketId, e.getMessage(), e);
            return 0;
        }
    }
}
