## ADDED Requirements

### Requirement: Per-asset pipeline computation uses a bounded number of database connections
The system SHALL perform all database operations for a single asset's per-run indicator or signal-state computation using one reused database connection, regardless of how many individual rows that asset's computation needs to read or write in that run.

#### Scenario: A backfill requiring many new rows for one asset does not multiply connection acquisitions
- **WHEN** an asset's per-run computation needs to persist many new rows (e.g. during a historical backfill covering many days)
- **THEN** the number of database connections acquired for that asset's computation does not scale with the number of rows persisted

### Requirement: Binance is crypto-only; real equities are sourced from Tiingo
The system SHALL NOT source STOCK or ETF asset data from Binance's tokenized perpetual contracts. Binance-provider assets SHALL be limited to the `CRYPTO` asset class, capped at a fixed maximum count by earliest onboarding order. Tiingo-provider assets SHALL likewise be capped at a fixed maximum count by earliest onboarding order.

#### Scenario: A previously-onboarded tokenized STOCK/ETF asset is deactivated
- **WHEN** a Binance-provider asset has asset class `STOCK` or `ETF`
- **THEN** it is deactivated (not deleted), preserving its historical data

#### Scenario: Onboarding beyond the cap deactivates the newest excess assets, not the earliest
- **WHEN** more than the capped number of assets exist for a provider
- **THEN** the assets retained active are the ones with the earliest insertion order (lowest id), and the excess (most recently added) are deactivated
