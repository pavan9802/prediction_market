# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Development Commands

### Maven Build Commands
```bash
cd server

# Build the project
mvn clean package

# Run the application
mvn spring-boot:run

# Run tests
mvn test

# Run a specific test class
mvn test -Dtest=PredictionMarketApplicationTests

# Clean build artifacts
mvn clean
```

### Project Structure
```
/server
├── pom.xml                           # Maven configuration (Spring Boot 4.0.1, Java 21)
└── src/main/java/com/prediction/market/prediction_market/
    ├── cache/                        # In-memory stores (PositionStore, MarketStore)
    ├── config/                       # Spring configuration beans
    ├── controllers/                  # REST API endpoints
    ├── engine/                       # Core execution engines (MarketEngine, PricingEngine)
    ├── entity/                       # Domain models and MongoDB documents
    ├── execution/                    # Per-market thread isolation (MarketExecutor, MarketExecutionRegistry)
    ├── repositories/                 # MongoDB repositories
    └── service/                      # Business logic services
```

## Core Architecture

### Ledger-First Pattern (CRITICAL)

**The transaction ledger is the source of truth for all balances.**

- `Transaction` entity is append-only and immutable
- Each transaction stores `balanceAfter` (running balance) for O(1) lookups
- `User.balance` is a CACHED/DERIVED value computed from the ledger
- `BalanceService.computeBalanceFromLedger(userId)` is the authoritative balance computation (O(1) via latest transaction)
- Scheduled reconciliation runs every 5 minutes using full scan to detect drift
- NEVER mutate `User.balance` directly - always append to the ledger first

**Running Balance Pattern:**
```java
// O(1) balance lookup - gets balanceAfter from latest transaction
double balance = balanceService.computeBalanceFromLedger(userId);

// When creating transaction:
double currentBalance = balanceService.computeBalanceFromLedger(userId);
double balanceAfter = currentBalance + transactionAmount;
transaction.setBalanceAfter(balanceAfter); // Store running balance
```

**Trade Balance Flow:**
1. Check balance from ledger: `balanceService.hasSufficientBalance(userId, cost)` (O(1))
2. Compute balanceAfter: `currentBalance + transactionAmount`
3. Atomic ledger append: `transactionRepository.save(transaction)` with unique nonce and balanceAfter
4. Async cache update: `balanceService.recomputeAndUpdateBalance(userId)`

### Per-Market Thread Isolation (CRITICAL)

**Each market has its own single-threaded ExecutorService ensuring deterministic trade ordering.**

```
TradeRequest
  → MarketExecutionRegistry.submitTrade()       # Routes to correct market
    → MarketExecutor (single-threaded pool)     # Per-market serialization
      → MarketEngine.executeTrade()              # Coordinator
        → OrderExecutionService.executeMarketOrder()  # Execution logic
```

**Why this matters:**
- Trades within a market execute sequentially (deterministic ordering)
- Trades across different markets execute in parallel
- No race conditions on market state
- Ensures FIFO processing per market

### Order Lifecycle State Machine

Orders follow a strict state machine enforced by `OrderStatus.canTransitionTo()`:

```
NEW (created, nonce established)
 ↓
OPEN (validated, ready for execution)
 ├→ PARTIAL (partially filled) - NOT IMPLEMENTED YET
 │   ├→ FILLED (completely filled) [TERMINAL]
 │   └→ CANCELLED [TERMINAL]
 ├→ FILLED [TERMINAL]
 ├→ CANCELLED [TERMINAL]
 └→ REJECTED [TERMINAL]
```

**Current Implementation:** Market orders fill completely (no partial fills yet)

**State Transition Methods:**
- `Order.transitionTo(OrderStatus)` - validates and transitions state
- `Order.reject(reason)` - transitions to REJECTED with reason
- `Order.fill(quantity, cost)` - updates filledQuantity and averageFillPrice

### Idempotency via Nonce

**All writes are idempotent using unique nonce constraints:**

- **Orders:** `Order.nonce` with unique sparse index
- **Transactions:** `Transaction.nonce` with unique sparse index
- Nonce format: `{userId}:{marketId}:{timestamp}:{UUID}`
- Transaction nonce: `{order.nonce}:tx`

**Idempotency Check Pattern:**
```java
// Check for existing order
Optional<Order> existing = orderRepository.findByNonce(nonce);
if (existing.isPresent()) {
    return existing.get();  // Return existing, don't re-execute
}

// Create new order with nonce
Order order = Order.builder().nonce(nonce).build();
orderRepository.save(order);  // MongoDB unique constraint prevents race
```

### Fixed-Precision Money

**Use the Money type for all financial calculations:**

```java
// DO THIS:
Money cost = Money.of(new BigDecimal("123.45"));
Money total = cost.multiply(quantity);

// NOT THIS:
double cost = 123.45;  // Floating-point rounding errors!
```

**Money Properties:**
- 8 decimal places (crypto-exchange precision)
- HALF_EVEN rounding (banker's rounding)
- Immutable and thread-safe
- Methods: `add()`, `subtract()`, `multiply()`, `divide()`, `negate()`, `abs()`

### In-Memory Cache with Idle Flush

**PositionStore and MarketStore provide low-latency hot paths:**

**How It Works:**
1. Data stored in `ConcurrentHashMap` (in-memory)
2. Lazy load from MongoDB on first access
3. Mark modified on write (timestamp)
4. Scheduled task flushes idle data every 1 second
5. If `(now - lastModified) > 1000ms`, persist to MongoDB asynchronously

**Why:**
- Reduces write amplification (batch writes)
- Low-latency reads/writes for hot markets
- Database remains source of truth

**Cache Methods:**
```java
// PositionStore
getOrCreatePosition(userId, marketId)  // Lazy load + cache
markPositionModified(userId, marketId)  // Mark for flush
flushIdlePositionsAndUsers()  // @Scheduled flush (1 sec idle)

// MarketStore
getMarketOrCreate(marketId)  // Lazy load + cache
markMarketModified(marketId)  // Mark for flush
flushIdleMarkets()  // @Scheduled flush (1 sec idle)
```

## Trade Execution Flow (Step-by-Step)

1. **API receives TradeRequest** → `MarketExecutionRegistry.submitTrade()`

2. **Per-market serialization** → `MarketExecutor.submit()` queues to single-threaded executor

3. **MarketEngine.executeTrade()** runs on market's thread
   - Extracts: userId, marketId, outcome, quantity, nonce
   - Delegates to `OrderExecutionService`

4. **OrderExecutionService.executeMarketOrder()**
   - Generate/validate nonce
   - **Idempotency check:** return existing order if duplicate nonce
   - Create Order (NEW status) → save (establishes nonce uniqueness)
   - Load MarketState from cache
   - **Strict validation:** `OrderValidator.validate()`
   - **State transition:** Order → OPEN
   - Call `executeOrder()`

5. **executeOrder() - Execution Phase**
   - Ensure user/position exist in cache
   - **Compute cost:** `PricingEngine.computeCost()` (LMSR)
   - **Balance check:** `BalanceService.hasSufficientBalance()` (from ledger)
   - **Atomic ledger append:** `TransactionRepository.save()` with nonce uniqueness
   - **Update order:** `order.fill(quantity, cost)` → status = FILLED
   - **Link transaction:** `orderRepository.linkTransaction(orderId, txId)`
   - **Update hot caches:**
     - Update Position (add shares to YES/NO)
     - Update MarketState (shares, price, timestamp)
   - **Mark for persistence:** `positionStore.markPositionModified()`
   - **Async balance update:** `balanceService.recomputeAndUpdateBalance()`

6. **Async persistence** (scheduled tasks)
   - PositionStore/MarketStore: flush idle data (1 sec threshold)
   - BalanceService: recompute balance from ledger

7. **Result:** `Order.totalCost` returned to caller (0 if rejected)

## LMSR Pricing Engine

**Logarithmic Market Scoring Rule (LMSR) provides deterministic pricing:**

```java
// Price calculation (0 to 1)
getPrice(yesShares, noShares, liquidityB)
  = expYes / (expYes + expNo)
  where expYes = exp((yesShares/b) - max)
        expNo  = exp((noShares/b) - max)
        max    = max(yesShares, noShares) / b

// Cost calculation
computeCost(yesShares, noShares, outcome, deltaShares, b)
  = newCost - oldCost  // LMSR cost function
```

**Key Properties:**
- Deterministic: same state → same price
- Continuous: prices update smoothly
- Automated market maker: no order book needed

## Validation & Constraints

**OrderValidator enforces strict constraints:**

- **Quantity:** [1, 1,000,000] shares
- **Cost:** [0.01, 1,000,000.00] currency units
- **Outcome:** "YES" or "NO" (case-insensitive)
- **Order Type:** MARKET only (limit orders not implemented)
- **Balance:** Sufficient for estimated cost + 10% slippage buffer
- **Market:** Must exist and be in OPEN status

**Validation Levels:**
1. Field validation (null checks, format)
2. Market state validation (exists, open)
3. Quantity validation (min/max)
4. Outcome validation (YES/NO)
5. Order type validation (MARKET only)
6. Balance validation (if BUY side)

## Database (MongoDB)

### Key Collections

**orders** - Order lifecycle tracking
- Unique index on `nonce`
- Compound indexes: `userId+marketId+createdAt`, `status+marketId`

**transactions** - Immutable ledger (source of truth for balances)
- Unique index on `nonce`
- Compound index: `userId+timestamp`

**users** - User accounts
- `balance` field is CACHED/DERIVED from ledger

**positions** - User shares per market
- Compound unique index: `userId+marketId`

**markets** - Market state
- Stores: yesShares, noShares, liquidityB, currentPrice, status

### Atomic Repository Methods

**OrderRepository provides race-safe updates:**

```java
// Atomic state transition with expected status check
long atomicStatusTransition(orderId, expectedStatus, newStatus, timestamp)

// Atomic fill update (only if OPEN or PARTIAL)
long atomicFill(orderId, filledQuantity, totalCost, averageFillPrice, newStatus, timestamp, completedAt)

// Atomic cancel (only if OPEN or PARTIAL)
long atomicCancel(orderId, timestamp)

// Atomic reject with reason
long atomicReject(orderId, reason, timestamp)
```

**Returns:** Count of modified documents (0 = race condition detected)

## Configuration

**AsyncConfig** - Thread pool for async persistence:
- Core pool: 4 threads
- Max pool: 8 threads
- Queue capacity: 100 tasks
- Thread prefix: "async-persistence-"

**Application Annotations:**
- `@EnableAsync` - enables `@Async` methods
- `@EnableScheduling` - enables `@Scheduled` methods

**MongoDB Connection:**
```properties
spring.mongodb.uri=[configured externally]
spring.mongodb.database=prediction-market
```

## Important Development Notes

### When Adding New Features

1. **For money-related changes:** Always use `Money` type with BigDecimal
2. **For state changes:** Use atomic repository methods with expected state checks
3. **For balance updates:** ALWAYS append to ledger first, then async cache update
4. **For idempotency:** Add unique nonce constraint on new write operations
5. **For validation:** Add checks to `OrderValidator` or create similar validator

### When Modifying Execution Flow

- Preserve per-market thread isolation (don't break MarketExecutor pattern)
- Keep database transactions tiny (single insert/update)
- Maintain idempotency (always check for existing nonce)
- Update both hot cache AND schedule async persistence

### When Debugging

1. **Balance mismatch?** Check `BalanceService.reconcileAllBalances()` logs
2. **Race condition?** Check atomic repository methods return count > 0
3. **Duplicate execution?** Verify nonce uniqueness and idempotency checks
4. **Price incorrect?** Verify LMSR inputs (yesShares, noShares, liquidityB)

### Testing Gaps (Opportunities)

- No unit tests for PricingEngine LMSR calculations
- No integration tests for order execution flow
- No tests for ledger-first balance consistency
- No tests for idempotency (duplicate nonce handling)
- No tests for per-market thread isolation
- No tests for cache flush behavior

## Technology Stack

- **Framework:** Spring Boot 4.0.1
- **Java:** 21
- **Database:** MongoDB (with Spring Data MongoDB)
- **Security:** Spring Security + JWT (jjwt 0.12.6)
- **Concurrency:** java.util.concurrent (ExecutorService, ConcurrentHashMap)
- **Build:** Maven
- **Utils:** Lombok (annotation processing)

## Tier 1 Non-Negotiables (CRITICAL)

These requirements are NEVER negotiable:

1. **Atomic balance updates** - no overdrafts, ledger-first pattern
2. **Ledger-based accounting** - transactions are source of truth
3. **Idempotent order placement** - unique nonce prevents duplicates
4. **Exactly-once balance effects** - no double debits/credits
5. **Order state machine** - strict transitions enforced
6. **Strict validation before accepting** - fail-fast validation
7. **No in-memory locks for money** - atomicity at database level
8. **Database transactions extremely small** - single insert/update
9. **Consistent rounding rules** - Money type with fixed precision
10. **Auditability** - can replay ledger to recompute balances

**Focus:** Market orders only (limit orders deferred)
