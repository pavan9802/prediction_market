package com.prediction.market.prediction_market.entity;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Fixed-precision money type for financial calculations.
 *
 * CRITICAL: Never use double/float for money in trading systems!
 * This class uses BigDecimal with fixed 8 decimal places (like crypto exchanges).
 *
 * Immutable and thread-safe.
 */
public final class Money implements Comparable<Money> {

    /**
     * Fixed scale for all monetary values (8 decimal places).
     * Chosen to match common crypto exchange precision.
     */
    public static final int SCALE = 8;

    /**
     * Rounding mode for all operations: HALF_EVEN (banker's rounding).
     * Prevents systematic bias in rounding errors.
     */
    public static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_EVEN;

    private final BigDecimal amount;

    // Common constants
    public static final Money ZERO = new Money(BigDecimal.ZERO);
    public static final Money ONE = new Money(BigDecimal.ONE);

    /**
     * Private constructor - use factory methods instead.
     */
    private Money(BigDecimal amount) {
        this.amount = amount.setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Create Money from BigDecimal.
     */
    public static Money of(BigDecimal amount) {
        if (amount == null) {
            throw new IllegalArgumentException("Amount cannot be null");
        }
        return new Money(amount);
    }

    /**
     * Create Money from double (use sparingly - prefer BigDecimal or String).
     */
    public static Money of(double amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    /**
     * Create Money from long (for whole amounts).
     */
    public static Money of(long amount) {
        return new Money(BigDecimal.valueOf(amount));
    }

    /**
     * Create Money from String (safest for parsing user input).
     */
    public static Money of(String amount) {
        if (amount == null || amount.trim().isEmpty()) {
            throw new IllegalArgumentException("Amount string cannot be null or empty");
        }
        try {
            return new Money(new BigDecimal(amount));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid amount format: " + amount, e);
        }
    }

    /**
     * Add two Money values.
     */
    public Money add(Money other) {
        return new Money(this.amount.add(other.amount));
    }

    /**
     * Subtract Money value.
     */
    public Money subtract(Money other) {
        return new Money(this.amount.subtract(other.amount));
    }

    /**
     * Multiply by a scalar value.
     */
    public Money multiply(BigDecimal scalar) {
        return new Money(this.amount.multiply(scalar));
    }

    /**
     * Multiply by integer (shares, quantity, etc.).
     */
    public Money multiply(int scalar) {
        return new Money(this.amount.multiply(BigDecimal.valueOf(scalar)));
    }

    /**
     * Divide by scalar value.
     */
    public Money divide(BigDecimal scalar) {
        if (scalar.compareTo(BigDecimal.ZERO) == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return new Money(this.amount.divide(scalar, SCALE, ROUNDING_MODE));
    }

    /**
     * Divide by integer.
     */
    public Money divide(int scalar) {
        if (scalar == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return new Money(this.amount.divide(BigDecimal.valueOf(scalar), SCALE, ROUNDING_MODE));
    }

    /**
     * Negate the amount.
     */
    public Money negate() {
        return new Money(this.amount.negate());
    }

    /**
     * Absolute value.
     */
    public Money abs() {
        return new Money(this.amount.abs());
    }

    /**
     * Check if positive (> 0).
     */
    public boolean isPositive() {
        return this.amount.compareTo(BigDecimal.ZERO) > 0;
    }

    /**
     * Check if negative (< 0).
     */
    public boolean isNegative() {
        return this.amount.compareTo(BigDecimal.ZERO) < 0;
    }

    /**
     * Check if zero.
     */
    public boolean isZero() {
        return this.amount.compareTo(BigDecimal.ZERO) == 0;
    }

    /**
     * Check if >= other.
     */
    public boolean isGreaterThanOrEqualTo(Money other) {
        return this.compareTo(other) >= 0;
    }

    /**
     * Check if <= other.
     */
    public boolean isLessThanOrEqualTo(Money other) {
        return this.compareTo(other) <= 0;
    }

    /**
     * Get underlying BigDecimal (for persistence/serialization only).
     */
    public BigDecimal toBigDecimal() {
        return amount;
    }

    /**
     * Convert to double (use only for display, never for calculations).
     */
    public double toDouble() {
        return amount.doubleValue();
    }

    @Override
    public int compareTo(Money other) {
        return this.amount.compareTo(other.amount);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Money money = (Money) obj;
        return amount.compareTo(money.amount) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(amount);
    }

    @Override
    public String toString() {
        return amount.toPlainString();
    }
}
