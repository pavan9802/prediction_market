package com.prediction.market.prediction_market.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import com.prediction.market.prediction_market.entity.Transaction;

import java.util.List;

@Repository
public interface TransactionRepository extends MongoRepository<Transaction, String> {
    List<Transaction> findByUserIdOrderByTimestampDesc(String userId);
    List<Transaction> findByMarketIdOrderByTimestampDesc(String marketId);

    /**
     * Check if a transaction with the given nonce already exists.
     * Used to ensure idempotency before attempting insert.
     *
     * @param nonce the unique transaction nonce
     * @return true if transaction exists, false otherwise
     */
    boolean existsByNonce(String nonce);

    /**
     * Find all transactions for a user to compute their balance.
     * Returns transactions in insertion order (by timestamp).
     *
     * @param userId the user ID
     * @return list of all transactions for the user
     */
    @Query("{ 'userId': ?0 }")
    List<Transaction> findAllByUserIdForBalanceCompute(String userId);

    /**
     * Find the most recent transaction for a user.
     * Used for O(1) balance lookup via balanceAfter field.
     *
     * @param userId the user ID
     * @return the latest transaction for the user, or null if none exist
     */
    Transaction findTopByUserIdOrderByTimestampDesc(String userId);
}
