package com.prediction.market.prediction_market.service;

import com.prediction.market.prediction_market.entity.Transaction;
import com.prediction.market.prediction_market.entity.User;
import com.prediction.market.prediction_market.repositories.TransactionRepository;
import com.prediction.market.prediction_market.repositories.UserRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for managing user balances based on ledger transactions.
 * The ledger (transactions collection) is the SOURCE OF TRUTH.
 * User.balance is a CACHED/DERIVED value computed from the ledger.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BalanceService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    /**
     * Compute a user's balance from the ledger.
     * Uses O(1) lookup via the balanceAfter field on the latest transaction.
     * This is the authoritative balance calculation.
     *
     * @param userId the user ID
     * @return the computed balance
     */
    public double computeBalanceFromLedger(String userId) {
        Transaction latestTransaction = transactionRepository.findTopByUserIdOrderByTimestampDesc(userId);

        if (latestTransaction != null) {
            return latestTransaction.getBalanceAfter();
        }

        return 0.0; // No transactions = zero balance
    }

    /**
     * Legacy method: Compute balance by summing all transactions.
     * Used for reconciliation and auditing purposes only.
     * WARNING: This is O(n) and should not be used in hot paths.
     *
     * @param userId the user ID
     * @return the computed balance
     */
    public double computeBalanceFromLedgerFullScan(String userId) {
        List<Transaction> transactions = transactionRepository.findAllByUserIdForBalanceCompute(userId);

        double balance = 0.0;
        for (Transaction tx : transactions) {
            balance += tx.getAmount(); // positive = credit, negative = debit
        }

        return balance;
    }

    /**
     * Recompute and update a user's cached balance from the ledger.
     * Called asynchronously after ledger updates.
     *
     * @param userId the user ID
     */
    @Async
    public void recomputeAndUpdateBalance(String userId) {
        try {
            double computedBalance = computeBalanceFromLedger(userId);

            Optional<User> userOpt = userRepository.findByUserId(userId);
            if (userOpt.isPresent()) {
                User user = userOpt.get();
                double oldBalance = user.getBalance();
                user.setBalance(computedBalance);
                userRepository.save(user);

                log.debug("Recomputed balance for user {}: {} -> {}",
                    userId, oldBalance, computedBalance);
            } else {
                log.warn("User not found for balance recompute: {}", userId);
            }
        } catch (Exception e) {
            log.error("Failed to recompute balance for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Check if user has sufficient balance for a trade.
     * Uses the authoritative ledger-computed balance.
     *
     * @param userId the user ID
     * @param amount the amount to check
     * @return true if user has sufficient balance
     */
    public boolean hasSufficientBalance(String userId, double amount) {
        double balance = computeBalanceFromLedger(userId);
        return balance >= amount;
    }

    /**
     * Periodic reconciliation job - ensures cached balances match ledger.
     * Runs every 5 minutes to detect and fix any drift.
     */
    @Scheduled(fixedDelay = 300000) // 5 minutes
    public void reconcileAllBalances() {
        log.info("Starting balance reconciliation from ledger...");

        try {
            List<User> users = userRepository.findAll();
            int reconciled = 0;
            int drifted = 0;

            for (User user : users) {
                double cachedBalance = user.getBalance();
                // Use full scan for auditing during reconciliation
                double ledgerBalance = computeBalanceFromLedgerFullScan(user.getUserId());

                // Allow small floating point tolerance (0.0001)
                if (Math.abs(cachedBalance - ledgerBalance) > 0.0001) {
                    log.warn("Balance drift detected for user {}: cached={}, ledger={}",
                        user.getUserId(), cachedBalance, ledgerBalance);
                    user.setBalance(ledgerBalance);
                    userRepository.save(user);
                    drifted++;
                }
                reconciled++;
            }

            log.info("Balance reconciliation complete: {} users checked, {} corrected",
                reconciled, drifted);
        } catch (Exception e) {
            log.error("Balance reconciliation failed: {}", e.getMessage(), e);
        }
    }
}
