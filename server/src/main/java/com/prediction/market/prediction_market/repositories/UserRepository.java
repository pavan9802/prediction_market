package com.prediction.market.prediction_market.repositories;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import com.prediction.market.prediction_market.entity.User;

import java.util.Optional;

@Repository
public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUserId(String userId);

    /**
     * NOTE: User.balance is now a CACHED/DERIVED value from the transactions ledger.
     * The ledger is the source of truth for balances.
     *
     * To update balances:
     *   1. Append to ledger (TransactionRepository.save())
     *   2. Recompute cached balance (BalanceService.recomputeAndUpdateBalance())
     *
     * The old atomic debit/credit methods have been removed in favor of the ledger-first pattern.
     */
}
