# Real-Time Prediction Market Backend — System Overview & Design

## Summary

This document describes the design of a real-time binary prediction market backend (YES/NO) using an LMSR automated market maker. It focuses on low-latency trade execution, deterministic pricing, and a clear separation of responsibilities between components.

## Table of Contents

- [Project Overview](#project-overview)
- [Goals & Scope](#goals--scope)
- [High-Level Architecture](#high-level-architecture)
- [Components & Responsibilities](#components--responsibilities)
- [Market Lifecycle](#market-lifecycle)
- [Execution Model](#execution-model)
- [In-Memory Cache Design](#in-memory-cache-design)
- [Database Schema](#database-schema)
- [Trade Execution Flow](#trade-execution-flow)
- [Persistence Strategy](#persistence-strategy)
- [Scaling Considerations](#scaling-considerations)
- [Recovery & Fault Tolerance](#recovery--fault-tolerance)
- [Interview / Resume-Friendly Explanation](#interview--resume-friendly-explanation)

---

## Project Overview

The system implements a binary prediction market where users buy and sell YES/NO outcome shares. Prices are determined deterministically by an LMSR pricing function and update in real time as trades arrive.

## Goals & Scope

**Goals**
- Low-latency trade execution
- Deterministic pricing and risk calculations
- Durable persistence for trades, balances, and positions
- Clear separation of components for maintainability and observability

**Out of scope (MVP)**
- KYC, fiat payments, fiat rails
- Cross-server sharding of markets (single-engine MVP)
- External market feeds / oracle integrations

## High-Level Architecture

Clients → API Layer → MarketEngine (single-threaded)
    → PricingEngine, RiskEngine, SettlementEngine

Caches (PositionStore, MarketStore) are hot paths; the Database is the source of truth. An EventBus streams updates (PriceUpdated, TradeExecuted, MarketResolved) to clients.

```
Clients -> API -> MarketEngine -> [Pricing, Risk, Settlement]
                                                                |
                                         PositionStore <-> MarketStore
                                                                |
                                                        Database
                                                                |
                                                         EventBus -> Clients
```

Key design decisions:
- Single-threaded MarketEngine for deterministic ordering
- In-memory caches for latency-sensitive state
- Immediate persistence for user funds and trades

## Components & Responsibilities

| Component | Responsibility |
|---|---|
| `MarketEngine` | Sequentially processes trades, enforces market rules, updates caches |
| `PricingEngine` | LMSR-based price and cost calculations |
| `RiskEngine` | Validates balances, reserves/locks collateral |
| `SettlementEngine` | Resolves markets and processes payouts |
| `PositionStore` (cache) | Active user balances & per-market positions |
| `MarketStore` (cache) | Market liquidity, prices, status |
| Database | Persistent storage for users, balances, trades, positions, markets |
| API Layer | Receives client requests, performs auth/validation, forwards to engine |
| EventBus | Publishes events for real-time clients and downstream services |

## Market Lifecycle

CREATED → OPEN → RESOLVED → SETTLED

- CREATED: Market registered, trading disabled
- OPEN: Trading enabled; prices update with activity
- RESOLVED: Outcome determined; trading closed
- SETTLED: Payouts processed; market closed

## Execution Model

- MarketEngine runs single-threaded to ensure deterministic processing order.
- Typical trade flow:
    1. Validate request at API layer
    2. Validate trade and required funds via `PositionStore` and `RiskEngine`
    3. Compute price/cost via `PricingEngine` (LMSR)
    4. Update in-memory `MarketStore` and `PositionStore`
    5. Persist trade and balance changes to DB
    6. Emit events to `EventBus`

- Note: In the current simulation implementation, each trade executes by choosing a random whole-number quantity between **10 and 100** (inclusive) and randomly buying or selling the selected outcome (YES or NO). This behavior is implemented in `MarketEngine.executeTrade()` for testing/demonstration.

## In-Memory Cache Design

**PositionStore**
- Map: `user_id -> UserState` (per active market)
- Stores: available & locked balances, per-outcome shares
- Eviction: TTL or LRU for inactive users

**MarketStore**
- Map: `market_id -> MarketState`
- Stores: yes_shares, no_shares, current_price, liquidity_b, status, last_updated
- Persisted via idle flush

Both caches are the low-latency source for the engine; the DB remains authoritative.

## Database Schema (examples)

Users
```sql
CREATE TABLE users (
    user_id VARCHAR PRIMARY KEY,
    username VARCHAR NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Balances
```sql
CREATE TABLE balances (
    user_id VARCHAR PRIMARY KEY REFERENCES users(user_id),
    available DECIMAL(20,6) NOT NULL,
    locked DECIMAL(20,6) NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Markets
```sql
CREATE TABLE markets (
    market_id VARCHAR PRIMARY KEY,
    name VARCHAR,
    status VARCHAR,
    outcome VARCHAR,
    liquidity_b DECIMAL(20,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

Positions
```sql
CREATE TABLE positions (
    user_id VARCHAR REFERENCES users(user_id),
    market_id VARCHAR REFERENCES markets(market_id),
    outcome VARCHAR,
    shares DECIMAL(20,6) NOT NULL,
    PRIMARY KEY(user_id, market_id, outcome)
);
```

Trades
```sql
CREATE TABLE trades (
    trade_id SERIAL PRIMARY KEY,
    user_id VARCHAR REFERENCES users(user_id),
    market_id VARCHAR REFERENCES markets(market_id),
    outcome VARCHAR,
    shares DECIMAL(20,6),
    price DECIMAL(20,6),
    cost DECIMAL(20,6),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Trade Execution Flow

1. Client submits trade to API
2. API validates auth and request
3. MarketEngine validates risk & computes cost
4. Update in-memory caches
5. Persist trade and balance updates immediately
6. Emit events to clients

## Persistence Strategy

| Data | Persist frequency |
|---|---|
| Trades | Immediately |
| User balances | Immediately |
| Market liquidity/prices | Idle flush or periodic snapshot |

Idle flush: each `MarketState` records `last_updated`; if idle threshold exceeded, snapshot to DB to reduce write amplification while keeping live cache updates in memory.

## Scaling Considerations

- Keep caches focused on hot users/markets. Evict via TTL/LRU.
- Future horizontal scaling: shard markets to multiple engines by `market_id`.
- Optionally introduce an external distributed cache (Redis) and a coordination/leader layer for cross-node consistency.

## Recovery & Fault Tolerance

- On restart, reload `MarketStore` and `PositionStore` from DB snapshots.
- Replay trades if snapshots are stale.
- Because trades and balances persist immediately, user funds are not lost.

