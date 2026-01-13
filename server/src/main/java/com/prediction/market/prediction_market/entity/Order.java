package com.prediction.market.prediction_market.entity;

import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.MongoId;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Order entity representing a user's intent to buy/sell shares in a market.
 *
 * For market orders (current implementation):
 * - Lifecycle: NEW → OPEN → FILLED (instant)
 * - No order book, immediate execution against AMM
 * - filledQuantity == quantity when FILLED
 *
 * For limit orders (future):
 * - Can remain OPEN until matched
 * - Supports PARTIAL fills
 * - Will require order book implementation
 *
 * IMPORTANT: This is the source of truth for order state.
 * State transitions must be atomic and validated.
 */
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document(collection = "orders")
@CompoundIndex(name = "user_market_idx", def = "{'userId':1,'marketId':1,'createdAt':-1}")
@CompoundIndex(name = "nonce_idx", def = "{'nonce':1}", unique = true, sparse = true)
@CompoundIndex(name = "status_market_idx", def = "{'status':1,'marketId':1,'createdAt':1}")
public class Order {

    @MongoId
    private String id;

    /**
     * Unique nonce for idempotency - prevents duplicate order submission.
     * Format: {userId}:{marketId}:{timestamp}:{randomUUID}
     */
    @Indexed(unique = true, sparse = true)
    private String nonce;

    @Indexed
    private String userId;

    @Indexed
    private String marketId;

    /**
     * Order type: MARKET or LIMIT (future).
     * Market orders execute immediately at current price.
     */
    @Builder.Default
    private String orderType = "MARKET";

    /**
     * Side: BUY or SELL.
     * For prediction markets, BUY means buying YES or NO shares.
     */
    private String side; // BUY, SELL

    /**
     * Outcome being traded: YES or NO.
     */
    private String outcome;

    /**
     * Requested quantity (number of shares).
     * Must be positive integer.
     */
    private int quantity;

    /**
     * Quantity filled so far.
     * For market orders: 0 initially, then equals quantity when FILLED.
     * For limit orders: can increase incrementally as order matches.
     */
    @Builder.Default
    private int filledQuantity = 0;

    /**
     * Limit price for LIMIT orders (future implementation).
     * For MARKET orders, this is null.
     * Stored as BigDecimal for precision.
     */
    private BigDecimal limitPrice;

    /**
     * Average fill price (cost per share).
     * Computed as totalCost / filledQuantity.
     * Stored as BigDecimal for precision.
     */
    private BigDecimal averageFillPrice;

    /**
     * Total cost of filled quantity (sum of all fills).
     * For BUY orders: amount debited from user balance.
     * For SELL orders: amount credited to user balance.
     * Stored as BigDecimal for precision.
     */
    private BigDecimal totalCost;

    /**
     * Current order status.
     * State machine: NEW → OPEN → FILLED/CANCELLED/REJECTED
     */
    @Indexed
    @Builder.Default
    private OrderStatus status = OrderStatus.NEW;

    /**
     * Timestamp when order was created (milliseconds since epoch).
     */
    private long createdAt;

    /**
     * Timestamp of last status update.
     */
    private long updatedAt;

    /**
     * Timestamp when order reached terminal state (FILLED/CANCELLED/REJECTED).
     * Null if order is still active.
     */
    private Long completedAt;

    /**
     * Rejection reason if status == REJECTED.
     */
    private String rejectionReason;

    /**
     * Reference to the transaction(s) that executed this order.
     * For audit trail linking.
     */
    private String transactionId;

    // ===== State Machine Methods =====

    /**
     * Transition order to a new status with validation.
     *
     * @param newStatus the target status
     * @throws IllegalStateException if transition is invalid
     */
    public void transitionTo(OrderStatus newStatus) {
        if (!this.status.canTransitionTo(newStatus)) {
            throw new IllegalStateException(
                String.format("Invalid order state transition: %s → %s (orderId=%s)",
                    this.status, newStatus, this.id)
            );
        }

        this.status = newStatus;
        this.updatedAt = System.currentTimeMillis();

        if (newStatus.isTerminal()) {
            this.completedAt = this.updatedAt;
        }
    }

    /**
     * Mark order as rejected with reason.
     */
    public void reject(String reason) {
        this.transitionTo(OrderStatus.REJECTED);
        this.rejectionReason = reason;
    }

    /**
     * Mark order as filled with execution details.
     */
    public void fill(int quantity, BigDecimal cost) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("Fill quantity must be positive");
        }
        if (cost == null || cost.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Fill cost must be positive");
        }

        this.filledQuantity += quantity;
        this.totalCost = (this.totalCost != null ? this.totalCost : BigDecimal.ZERO).add(cost);
        this.averageFillPrice = this.totalCost.divide(
            BigDecimal.valueOf(this.filledQuantity),
            Money.SCALE,
            Money.ROUNDING_MODE
        );

        if (this.filledQuantity == this.quantity) {
            // Completely filled
            if (this.status == OrderStatus.NEW || this.status == OrderStatus.OPEN) {
                this.transitionTo(OrderStatus.FILLED);
            } else if (this.status == OrderStatus.PARTIAL) {
                this.transitionTo(OrderStatus.FILLED);
            }
        } else if (this.filledQuantity < this.quantity) {
            // Partially filled
            if (this.status == OrderStatus.OPEN) {
                this.transitionTo(OrderStatus.PARTIAL);
            }
        } else {
            throw new IllegalStateException("Overfill: filledQuantity > quantity");
        }
    }

    /**
     * Get remaining quantity to be filled.
     */
    public int getRemainingQuantity() {
        return this.quantity - this.filledQuantity;
    }

    /**
     * Check if order is completely filled.
     */
    public boolean isFilled() {
        return this.filledQuantity == this.quantity && this.status == OrderStatus.FILLED;
    }

    /**
     * Check if order is still active (can be executed or cancelled).
     */
    public boolean isActive() {
        return this.status.isActive();
    }
}
