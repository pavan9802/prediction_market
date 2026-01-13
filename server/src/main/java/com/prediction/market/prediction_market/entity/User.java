package com.prediction.market.prediction_market.entity;

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
@NoArgsConstructor(access = AccessLevel.PROTECTED) // required by JPA
@AllArgsConstructor
@Builder
@Document(collection = "users")
public class User {
    @MongoId
    private String id;

    private String userId;

    /**
     * CACHED/DERIVED BALANCE - Not source of truth!
     *
     * The transactions ledger is the SOURCE OF TRUTH for balances.
     * This field is a cached snapshot computed from the ledger for performance.
     *
     * To get the authoritative balance:
     *   balanceService.computeBalanceFromLedger(userId)
     *
     * To update balance (ledger-first pattern):
     *   1. Append transaction to ledger (transactionRepository.save())
     *   2. Async recompute this cached value (balanceService.recomputeAndUpdateBalance())
     *
     * This cached value is reconciled with the ledger every 5 minutes.
     *
     * NEVER mutate this field directly for balance changes!
     */
    private double balance;
}
