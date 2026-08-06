## ADDED Requirements

### Requirement: Scan matches report asset liquidity
Every `ScanResult` returned by `POST /api/v1/scan` SHALL include the matched asset's
`avgVolume7d` (rolling 7-day average D1 volume), computed once per asset regardless of how many
conditions it matched.

#### Scenario: An asset with liquidity data matches one or more conditions
- **WHEN** an asset matches every condition in a scan request (or, for `OR`, at least one)
- **THEN** its `ScanResult` includes `avgVolume7d` equal to the same value `/api/v1/signals`
  would report for that asset

#### Scenario: An asset has no recorded volume
- **WHEN** an asset matches a condition but has no corresponding row in the underlying liquidity
  data
- **THEN** its `ScanResult`'s `avgVolume7d` is `0`, not null and not an error - matching
  `/api/v1/signals`' existing behavior for the same case

### Requirement: Scan results can be sorted by liquidity
`POST /api/v1/scan` SHALL accept an optional `sort` field on the request body. When omitted or
set to `SYMBOL_ASC`, results are ordered by symbol ascending (today's existing, unchanged
behavior). When set to `LIQUIDITY_DESC`, results are ordered by `avgVolume7d` descending.

#### Scenario: No sort specified
- **WHEN** a scan request body omits `sort`
- **THEN** results are returned in symbol-ascending order, identical to current behavior

#### Scenario: Liquidity sort requested
- **WHEN** a scan request body sets `sort` to `LIQUIDITY_DESC`
- **THEN** results are returned ordered by `avgVolume7d` descending, highest first

#### Scenario: Liquidity sort combined with a limit
- **WHEN** a scan request sets both `sort=LIQUIDITY_DESC` and a `limit`
- **THEN** the returned results are the top-`limit` most liquid matches, not an arbitrary
  `limit`-sized subset that happens to then be sorted
