package com.prediction.market.prediction_market.service;

import com.prediction.market.prediction_market.cache.MarketStore;
import com.prediction.market.prediction_market.cache.PositionStore;
import com.prediction.market.prediction_market.engine.PricingEngine;
import com.prediction.market.prediction_market.entity.*;
import com.prediction.market.prediction_market.repositories.OrderRepository;
import com.prediction.market.prediction_market.repositories.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Order execution service - handles complete order lifecycle.
 *
 * Order Flow (Market Orders):
 * 1. Receive order request
 * 2. Create Order entity with NEW status
 * 3. Strict validation (OrderValidator)
 * 4. Check balance from ledger (BalanceService)
 * 5. Transition to OPEN
 * 6. Execute against AMM (immediate for market orders)
 * 7. Append to ledger (atomic transaction insert)
 * 8. Update order status to FILLED
 * 9. Update market state and positions (hot path)
 * 10. Async balance recompute
 *
 * CRITICAL PROPERTIES:
 * - Idempotent: duplicate nonce returns existing order
 * - Atomic: ledger append is the source of truth
 * - Validated: all orders pass strict validation
 * - State machine: proper lifecycle tracking
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderExecutionService {

    private final OrderRepository orderRepository;
    private final OrderValidator orderValidator;
    private final BalanceService balanceService;
    private final TransactionRepository transactionRepository;
    private final MarketStore marketStore;
    private final PositionStore positionStore;
    private final PricingEngine pricingEngine;

    /**
     * Execute a market order (buy shares).
     *
     * @param userId the user ID
     * @param marketId the market ID
     * @param outcome YES or NO
     * @param quantity number of shares to buy
     * @param clientNonce client-provided nonce for idempotency (optional)
     * @return the executed order
     */
    public Order executeMarketOrder(
            String userId,
            String marketId,
            String outcome,
            int quantity,
            String clientNonce) {

        long timestamp = System.currentTimeMillis();

        // Generate nonce (use client nonce if provided, otherwise generate)
        String nonce = clientNonce != null ? clientNonce :
            String.format("%s:%s:%d:%s", userId, marketId, timestamp, UUID.randomUUID());

        // IDEMPOTENCY CHECK: Return existing order if duplicate
        var existingOrder = orderRepository.findByNonce(nonce);
        if (existingOrder.isPresent()) {
            log.info("Duplicate order request detected, returning existing order: nonce={}", nonce);
            return existingOrder.get();
        }

        // Create Order entity (NEW status)
        Order order = Order.builder()
                .nonce(nonce)
                .userId(userId)
                .marketId(marketId)
                .orderType("MARKET")
                .side("BUY")
                .outcome(outcome)
                .quantity(quantity)
                .status(OrderStatus.NEW)
                .createdAt(timestamp)
                .updatedAt(timestamp)
                .build();

        // Save order in NEW status (establishes nonce uniqueness)
        try {
            order = orderRepository.save(order);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                // Race condition: another thread created order with same nonce
                log.info("Race condition on order creation, fetching existing order: nonce={}", nonce);
                return orderRepository.findByNonce(nonce).orElseThrow();
            }
            throw new RuntimeException("Failed to create order", e);
        }

        // Get market state
        MarketState marketState = marketStore.getMarketOrCreate(marketId);
        if (marketState == null) {
            order.reject("Market not found: " + marketId);
            orderRepository.save(order);
            throw new IllegalArgumentException("Market not found: " + marketId);
        }

        // STRICT VALIDATION
        OrderValidator.ValidationResult validation = orderValidator.validate(order, marketState);
        if (!validation.isValid()) {
            String reason = validation.getErrorMessage();
            log.warn("Order rejected: {} (orderId={}, userId={})", reason, order.getId(), userId);
            order.reject(reason);
            orderRepository.save(order);
            throw new IllegalArgumentException("Order validation failed: " + reason);
        }

        // Transition to OPEN (validation passed)
        order.transitionTo(OrderStatus.OPEN);
        orderRepository.save(order);

        // Execute order
        try {
            executeOrder(order, marketState);
            return order;
        } catch (Exception e) {
            log.error("Order execution failed: orderId={}, error={}", order.getId(), e.getMessage(), e);
            order.reject("Execution failed: " + e.getMessage());
            orderRepository.save(order);
            throw new RuntimeException("Order execution failed", e);
        }
    }

    /**
     * Execute order against market (AMM for market orders).
     */
    private void executeOrder(Order order, MarketState marketState) {
        String userId = order.getUserId();
        String marketId = order.getMarketId();
        String outcome = order.getOutcome();
        int quantity = order.getQuantity();

        // Ensure user/position exists in cache
        positionStore.getOrCreateUser(userId);
        Position position = positionStore.getOrCreatePosition(userId, marketId);

        // Compute cost using pricing engine
        double costDouble = pricingEngine.computeCost(
                marketState.getYesShares(),
                marketState.getNoShares(),
                outcome,
                quantity,
                marketState.getLiquidityB());

        Money cost = Money.of(costDouble);

        // Final balance check from ledger (source of truth)
        if (!balanceService.hasSufficientBalance(userId, cost.toDouble())) {
            throw new IllegalStateException("Insufficient balance at execution time");
        }

        // ATOMIC LEDGER APPEND: Source of truth for balance changes
        long timestamp = System.currentTimeMillis();

        // Compute balanceAfter: get current balance and add transaction amount
        double currentBalance = balanceService.computeBalanceFromLedger(userId);
        double transactionAmount = -cost.toDouble(); // negative = debit
        double balanceAfter = currentBalance + transactionAmount;

        Transaction transaction = Transaction.builder()
                .userId(userId)
                .marketId(marketId)
                .transactionType("TRADE_BUY")
                .amount(transactionAmount)
                .outcome(outcome)
                .shares(quantity)
                .price(cost.toDouble() / quantity) // average price per share
                .timestamp(timestamp)
                .nonce(order.getNonce() + ":tx") // Link to order nonce
                .status("COMPLETED")
                .balanceAfter(balanceAfter) // Store running balance
                .build();

        try {
            transaction = transactionRepository.save(transaction);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("duplicate key")) {
                log.warn("Duplicate transaction for order, skipping: orderId={}", order.getId());
                // Transaction already exists, order likely already executed
                return;
            }
            throw new RuntimeException("Ledger append failed", e);
        }

        // Ledger append successful - update order status to FILLED
        order.fill(quantity, cost.toBigDecimal());
        orderRepository.save(order);

        // Link transaction to order
        orderRepository.linkTransaction(order.getId(), transaction.getId());

        // Update hot-path caches (market state and positions)
        if (outcome.equalsIgnoreCase("YES")) {
            position.addYesShares(quantity);
            marketState.setYesShares(marketState.getYesShares() + quantity);
        } else {
            position.addNoShares(quantity);
            marketState.setNoShares(marketState.getNoShares() + quantity);
        }

        marketState.setLastTradeTimestamp(timestamp);
        double newPrice = pricingEngine.getPrice(
                marketState.getYesShares(),
                marketState.getNoShares(),
                marketState.getLiquidityB());
        marketState.setCurrentPrice(newPrice);

        // Mark for async persistence
        positionStore.markPositionModified(userId, marketId);

        // Async balance recompute
        balanceService.recomputeAndUpdateBalance(userId);

        log.info("Order executed: orderId={}, userId={}, market={}, outcome={}, qty={}, cost={}",
                order.getId(), userId, marketId, outcome, quantity, cost);
    }

    /**
     * Cancel an order (for limit orders in future).
     */
    public void cancelOrder(String orderId, String userId) {
        var orderOpt = orderRepository.findById(orderId);
        if (orderOpt.isEmpty()) {
            throw new IllegalArgumentException("Order not found: " + orderId);
        }

        Order order = orderOpt.get();

        // Verify ownership
        if (!order.getUserId().equals(userId)) {
            throw new SecurityException("Cannot cancel order owned by different user");
        }

        // Check if order is cancellable
        if (!order.isActive()) {
            throw new IllegalStateException("Order is not active, cannot cancel: " + order.getStatus());
        }

        // Atomic cancel
        long timestamp = System.currentTimeMillis();
        long updated = orderRepository.atomicCancel(orderId, timestamp);

        if (updated == 0) {
            // Order state changed between check and cancel (race condition)
            throw new IllegalStateException("Order state changed, cannot cancel");
        }

        log.info("Order cancelled: orderId={}, userId={}", orderId, userId);
    }
}
