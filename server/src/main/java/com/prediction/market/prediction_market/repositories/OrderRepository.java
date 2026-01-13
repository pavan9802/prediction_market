package com.prediction.market.prediction_market.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.data.mongodb.repository.Update;
import org.springframework.stereotype.Repository;

import com.prediction.market.prediction_market.entity.Order;
import com.prediction.market.prediction_market.entity.OrderStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Repository for Order entity with atomic state updates.
 *
 * CRITICAL: All state transitions must be atomic to prevent race conditions
 * when multiple threads/servers process the same order.
 */
@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    /**
     * Find order by unique nonce (idempotency check).
     */
    Optional<Order> findByNonce(String nonce);

    /**
     * Check if order with nonce already exists.
     */
    boolean existsByNonce(String nonce);

    /**
     * Find all orders for a user in a specific market.
     */
    List<Order> findByUserIdAndMarketIdOrderByCreatedAtDesc(String userId, String marketId);

    /**
     * Find all orders for a user across all markets.
     */
    List<Order> findByUserIdOrderByCreatedAtDesc(String userId);

    /**
     * Find active orders for a market (for order book - future use).
     */
    @Query("{ 'marketId': ?0, 'status': { $in: ['OPEN', 'PARTIAL'] } }")
    List<Order> findActiveOrdersByMarketId(String marketId);

    /**
     * Atomically transition order from expected status to new status.
     * This prevents race conditions when multiple threads try to update the same order.
     *
     * @param orderId the order ID
     * @param expectedStatus the expected current status
     * @param newStatus the new status to set
     * @param timestamp the update timestamp
     * @return number of documents modified (1 if successful, 0 if status mismatch)
     */
    @Query("{ 'id': ?0, 'status': ?1 }")
    @Update("{ $set: { 'status': ?2, 'updatedAt': ?3 } }")
    long atomicStatusTransition(String orderId, OrderStatus expectedStatus, OrderStatus newStatus, long timestamp);

    /**
     * Atomically mark order as filled with execution details.
     * Only succeeds if order is in OPEN or PARTIAL state.
     *
     * @param orderId the order ID
     * @param filledQuantity the new filled quantity
     * @param totalCost the total cost of fills
     * @param averageFillPrice the average fill price
     * @param newStatus the new status (PARTIAL or FILLED)
     * @param timestamp the update timestamp
     * @param completedAt the completion timestamp (for terminal states)
     * @return number of documents modified
     */
    @Query("{ 'id': ?0, 'status': { $in: ['OPEN', 'PARTIAL'] } }")
    @Update("{ $set: { " +
            "'filledQuantity': ?1, " +
            "'totalCost': ?2, " +
            "'averageFillPrice': ?3, " +
            "'status': ?4, " +
            "'updatedAt': ?5, " +
            "'completedAt': ?6 " +
            "} }")
    long atomicFill(
        String orderId,
        int filledQuantity,
        BigDecimal totalCost,
        BigDecimal averageFillPrice,
        OrderStatus newStatus,
        long timestamp,
        Long completedAt
    );

    /**
     * Atomically cancel an order.
     * Only succeeds if order is in OPEN or PARTIAL state.
     *
     * @param orderId the order ID
     * @param timestamp the update timestamp
     * @return number of documents modified
     */
    @Query("{ 'id': ?0, 'status': { $in: ['OPEN', 'PARTIAL'] } }")
    @Update("{ $set: { 'status': 'CANCELLED', 'updatedAt': ?1, 'completedAt': ?1 } }")
    long atomicCancel(String orderId, long timestamp);

    /**
     * Atomically reject an order with reason.
     * Only succeeds if order is in NEW or OPEN state.
     *
     * @param orderId the order ID
     * @param reason the rejection reason
     * @param timestamp the update timestamp
     * @return number of documents modified
     */
    @Query("{ 'id': ?0, 'status': { $in: ['NEW', 'OPEN'] } }")
    @Update("{ $set: { 'status': 'REJECTED', 'rejectionReason': ?1, 'updatedAt': ?2, 'completedAt': ?2 } }")
    long atomicReject(String orderId, String reason, long timestamp);

    /**
     * Link transaction to order (for audit trail).
     *
     * @param orderId the order ID
     * @param transactionId the transaction ID
     * @return number of documents modified
     */
    @Query("{ 'id': ?0 }")
    @Update("{ $set: { 'transactionId': ?1 } }")
    long linkTransaction(String orderId, String transactionId);
}
