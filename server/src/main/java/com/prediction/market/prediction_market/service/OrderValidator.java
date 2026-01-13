package com.prediction.market.prediction_market.service;

import com.prediction.market.prediction_market.entity.Money;
import com.prediction.market.prediction_market.entity.Order;
import com.prediction.market.prediction_market.entity.MarketState;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Strict order validation service.
 *
 * CRITICAL: All orders MUST pass validation before being accepted.
 * Invalid orders are rejected immediately with clear error messages.
 *
 * Validation Philosophy:
 * - Fail fast: reject invalid orders at the gate
 * - Clear errors: provide actionable rejection reasons
 * - Defensive: assume all input is malicious until proven otherwise
 * - No side effects: validation is read-only
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OrderValidator {

    private final BalanceService balanceService;

    // Validation constraints
    private static final int MIN_QUANTITY = 1;
    private static final int MAX_QUANTITY = 1_000_000;
    private static final Money MIN_COST = Money.of("0.01"); // Minimum $0.01
    private static final Money MAX_COST = Money.of("1000000.00"); // Maximum $1M per order

    /**
     * Result of order validation.
     */
    public static class ValidationResult {
        private final boolean valid;
        private final List<String> errors;

        private ValidationResult(boolean valid, List<String> errors) {
            this.valid = valid;
            this.errors = errors;
        }

        public static ValidationResult valid() {
            return new ValidationResult(true, List.of());
        }

        public static ValidationResult invalid(List<String> errors) {
            return new ValidationResult(false, errors);
        }

        public boolean isValid() {
            return valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public String getErrorMessage() {
            return String.join("; ", errors);
        }
    }

    /**
     * Validate order before acceptance.
     *
     * @param order the order to validate
     * @param marketState the current market state
     * @return validation result
     */
    public ValidationResult validate(Order order, MarketState marketState) {
        List<String> errors = new ArrayList<>();

        // 1. Basic field validation
        validateFields(order, errors);

        // 2. Market state validation
        validateMarketState(marketState, errors);

        // 3. Quantity validation
        validateQuantity(order, errors);

        // 4. Outcome validation
        validateOutcome(order, errors);

        // 5. Order type validation
        validateOrderType(order, errors);

        // 6. User balance validation (if BUY order)
        if (errors.isEmpty() && "BUY".equals(order.getSide())) {
            validateBalance(order, marketState, errors);
        }

        if (errors.isEmpty()) {
            return ValidationResult.valid();
        } else {
            log.warn("Order validation failed: {} (orderId={}, userId={})",
                errors, order.getId(), order.getUserId());
            return ValidationResult.invalid(errors);
        }
    }

    private void validateFields(Order order, List<String> errors) {
        if (order.getUserId() == null || order.getUserId().trim().isEmpty()) {
            errors.add("userId is required");
        }
        if (order.getMarketId() == null || order.getMarketId().trim().isEmpty()) {
            errors.add("marketId is required");
        }
        if (order.getSide() == null || order.getSide().trim().isEmpty()) {
            errors.add("side is required");
        }
        if (order.getOutcome() == null || order.getOutcome().trim().isEmpty()) {
            errors.add("outcome is required");
        }
        if (order.getNonce() == null || order.getNonce().trim().isEmpty()) {
            errors.add("nonce is required for idempotency");
        }
    }

    private void validateMarketState(MarketState marketState, List<String> errors) {
        if (marketState == null) {
            errors.add("Market not found");
            return;
        }

        // Check if market is open for trading
        // TODO: Add market status field (OPEN, CLOSED, RESOLVED)
        // For now, assume all markets are open
    }

    private void validateQuantity(Order order, List<String> errors) {
        int quantity = order.getQuantity();

        if (quantity < MIN_QUANTITY) {
            errors.add(String.format("Quantity must be at least %d", MIN_QUANTITY));
        }
        if (quantity > MAX_QUANTITY) {
            errors.add(String.format("Quantity cannot exceed %d", MAX_QUANTITY));
        }
    }

    private void validateOutcome(Order order, List<String> errors) {
        String outcome = order.getOutcome();
        if (outcome == null) {
            return; // Already caught in validateFields
        }

        if (!outcome.equalsIgnoreCase("YES") && !outcome.equalsIgnoreCase("NO")) {
            errors.add("Outcome must be YES or NO");
        }
    }

    private void validateOrderType(Order order, List<String> errors) {
        String orderType = order.getOrderType();
        if (orderType == null) {
            return;
        }

        if (!orderType.equals("MARKET")) {
            errors.add("Only MARKET orders are supported currently");
        }

        // Future: validate LIMIT orders
        if ("LIMIT".equals(orderType) && order.getLimitPrice() == null) {
            errors.add("Limit price is required for LIMIT orders");
        }
    }

    private void validateBalance(Order order, MarketState marketState, List<String> errors) {
        // For BUY orders, estimate cost and check if user has sufficient balance
        // Note: This is an estimate. Actual cost is computed at execution time.

        try {
            // Estimate cost using current market state
            // This is a rough check - actual cost may differ slightly due to slippage
            double estimatedCost = estimateOrderCost(order, marketState);
            Money requiredBalance = Money.of(estimatedCost);

            // Check constraints
            if (requiredBalance.isLessThanOrEqualTo(Money.ZERO)) {
                errors.add("Estimated order cost must be positive");
                return;
            }
            if (requiredBalance.compareTo(MIN_COST) < 0) {
                errors.add(String.format("Order cost must be at least %s", MIN_COST));
                return;
            }
            if (requiredBalance.compareTo(MAX_COST) > 0) {
                errors.add(String.format("Order cost cannot exceed %s", MAX_COST));
                return;
            }

            // Check user balance
            Money userBalance = Money.of(balanceService.computeBalanceFromLedger(order.getUserId()));
            if (userBalance.compareTo(requiredBalance) < 0) {
                errors.add(String.format(
                    "Insufficient balance: have %s, need ~%s",
                    userBalance, requiredBalance
                ));
            }

        } catch (Exception e) {
            errors.add("Failed to validate balance: " + e.getMessage());
            log.error("Balance validation error for order: {}", order.getId(), e);
        }
    }

    /**
     * Estimate cost for an order (rough approximation for validation).
     * Actual cost will be computed by PricingEngine at execution time.
     *
     * This is intentionally conservative (overestimates) to avoid accepting
     * orders that will fail at execution.
     */
    private double estimateOrderCost(Order order, MarketState marketState) {
        // Simplified cost estimation
        // In reality, LMSR cost is non-linear and depends on current pool state

        double yesShares = marketState.getYesShares();
        double noShares = marketState.getNoShares();
        double b = marketState.getLiquidityB();
        int quantity = order.getQuantity();

        // Rough estimate: cost ≈ quantity * current_price + slippage_buffer
        // Add 10% buffer for slippage and price movement
        double currentPrice = marketState.getCurrentPrice();

        if (order.getOutcome().equalsIgnoreCase("YES")) {
            return quantity * currentPrice * 1.1; // 10% slippage buffer
        } else {
            return quantity * (1.0 - currentPrice) * 1.1; // 10% slippage buffer
        }
    }
}
