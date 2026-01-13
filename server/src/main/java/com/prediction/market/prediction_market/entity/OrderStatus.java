package com.prediction.market.prediction_market.entity;

/**
 * Order state machine for strict lifecycle management.
 *
 * State Transitions (STRICT - no shortcuts allowed):
 *
 * NEW → OPEN      (order validated and accepted)
 * NEW → REJECTED  (failed validation)
 *
 * OPEN → PARTIAL  (partially filled)
 * OPEN → FILLED   (completely filled)
 * OPEN → CANCELLED (user cancelled)
 * OPEN → REJECTED (system rejected after acceptance - rare)
 *
 * PARTIAL → FILLED   (remaining quantity filled)
 * PARTIAL → CANCELLED (user cancelled partial order)
 *
 * Terminal states: FILLED, CANCELLED, REJECTED
 * (Once reached, no further transitions allowed)
 */
public enum OrderStatus {

    /**
     * NEW: Order received but not yet validated.
     * Next: OPEN (accepted) or REJECTED (validation failed)
     */
    NEW,

    /**
     * OPEN: Order validated and waiting for execution.
     * For market orders, this is very brief (instant fill).
     * For limit orders (future), this persists until match or cancel.
     * Next: PARTIAL, FILLED, CANCELLED, or REJECTED
     */
    OPEN,

    /**
     * PARTIAL: Order partially filled, remaining quantity still open.
     * Only applicable to limit orders (future implementation).
     * Market orders should go directly NEW → OPEN → FILLED.
     * Next: FILLED or CANCELLED
     */
    PARTIAL,

    /**
     * FILLED: Order completely executed.
     * TERMINAL STATE - no further transitions.
     */
    FILLED,

    /**
     * CANCELLED: Order cancelled by user or system.
     * TERMINAL STATE - no further transitions.
     */
    CANCELLED,

    /**
     * REJECTED: Order rejected due to validation failure or system error.
     * TERMINAL STATE - no further transitions.
     */
    REJECTED;

    /**
     * Check if this is a terminal state (no further transitions allowed).
     */
    public boolean isTerminal() {
        return this == FILLED || this == CANCELLED || this == REJECTED;
    }

    /**
     * Check if this is an active state (order can still be executed).
     */
    public boolean isActive() {
        return this == OPEN || this == PARTIAL;
    }

    /**
     * Validate state transition is legal.
     *
     * @param to the target state
     * @return true if transition is valid
     */
    public boolean canTransitionTo(OrderStatus to) {
        if (this.isTerminal()) {
            return false; // No transitions from terminal states
        }

        return switch (this) {
            case NEW -> to == OPEN || to == REJECTED;
            case OPEN -> to == PARTIAL || to == FILLED || to == CANCELLED || to == REJECTED;
            case PARTIAL -> to == FILLED || to == CANCELLED;
            default -> false;
        };
    }
}
