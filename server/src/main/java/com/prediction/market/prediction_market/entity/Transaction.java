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

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
@Document(collection = "transactions")
@CompoundIndex(name = "user_timestamp_idx", def = "{'userId':1,'timestamp':-1}")
@CompoundIndex(name = "nonce_idx", def = "{'nonce':1}", unique = true, sparse = true)
public class Transaction {
    @MongoId
    private String id;

    @Indexed
    private String userId;

    private String marketId;
    private String transactionType; // TRADE_BUY, TRADE_SELL, MARKET_RESOLUTION, DEPOSIT, WITHDRAWAL
    private double amount; // positive for credit, negative for debit
    private String outcome; // YES or NO (for trades)
    private int shares; // number of shares traded
    private double price; // price per share at time of trade

    private long timestamp;
    private String referenceId; // optional reference to related entity (e.g., trade ID)

    /**
     * Nonce for idempotency - prevents duplicate transactions.
     * Format: {userId}:{marketId}:{timestamp}:{randomUUID}
     * Unique index ensures atomic insert-only-once semantics.
     */
    @Indexed(unique = true, sparse = true)
    private String nonce;

    @Builder.Default
    private String status = "COMPLETED"; // COMPLETED, PENDING, FAILED

    /**
     * Running balance after this transaction.
     * This allows O(1) balance lookups by fetching the latest transaction.
     * balanceAfter = balanceBefore + amount
     */
    private double balanceAfter;
}
