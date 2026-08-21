## ADDED Requirements

### Requirement: Provisional W1 trend computation
The system SHALL compute a provisional W1 trend direction and flip-level price for an asset by synthesizing a week-to-date candle from this week's already-ingested D1 candles (Monday's open through the latest ingested D1 close) and running it through the existing SuperTrend calculation, entirely in-memory.

#### Scenario: Mid-week request with partial week data
- **WHEN** a provisional read is requested for an asset partway through the current week
- **THEN** the system returns a provisional trend direction and flip-level price derived from the D1 candles ingested so far this week

#### Scenario: No D1 candles ingested yet for the current week
- **WHEN** a provisional read is requested for an asset with no D1 candles ingested since the current week's Monday
- **THEN** the system returns no provisional read for that asset (there is nothing yet to synthesize)

### Requirement: Provisional read is never persisted
The system SHALL NOT write the provisional read to `signal_state`, `signal_event`, the `candles` table, or any other persisted store. The provisional read SHALL be recomputed on every request from currently-ingested D1 data.

#### Scenario: New D1 data ingested between two requests
- **WHEN** a provisional read is requested, new D1 data is ingested for that asset, and the provisional read is requested again
- **THEN** the second request's result reflects the newly ingested candle, demonstrating the value is recomputed rather than cached or persisted

### Requirement: Divergence-only reporting
The system SHALL only treat a provisional read as noteworthy when its direction differs from the asset's last *committed* W1 `TrendState`. A provisional read that agrees with the committed state SHALL NOT be flagged as forming or at risk.

#### Scenario: Provisional direction agrees with committed state
- **WHEN** an asset's provisional W1 direction matches its last committed W1 `TrendState`
- **THEN** the asset is not flagged as a forming flip or a risk in either weekly briefing

#### Scenario: Provisional direction diverges from committed state
- **WHEN** an asset's provisional W1 direction differs from its last committed W1 `TrendState`
- **THEN** the asset is eligible to be flagged in the relevant weekly briefing section

### Requirement: W1-only scope
The system SHALL NOT compute or expose a provisional read for D1 or any other timeframe as part of this capability.

#### Scenario: Provisional read requested conceptually for D1
- **WHEN** any code path in this capability considers timeframes other than W1
- **THEN** no provisional computation or field exists for those timeframes

### Requirement: Surfaced in weekly-pullback-briefing as an inline risk flag
Each pullback candidate in the `weekly-pullback-briefing` report SHALL show its provisional W1 read inline, directly next to that candidate, whenever the provisional read diverges from the committed W1 state the candidate was selected on.

#### Scenario: Bullish pullback candidate with diverging provisional read
- **WHEN** a bullish pullback candidate's committed W1 state is BULLISH but its provisional W1 read is BEARISH
- **THEN** the report shows a caution annotation for that candidate identifying both the committed and provisional states

#### Scenario: Bullish pullback candidate with agreeing provisional read
- **WHEN** a bullish pullback candidate's provisional W1 read still agrees with its committed BULLISH state
- **THEN** no provisional annotation is shown for that candidate

### Requirement: Surfaced in weekly-trend-briefing as a separate "Flips Forming" section
The `weekly-trend-briefing` report SHALL include a distinct section, separate from its existing confluence and scan-candidate lists, listing assets that are not currently confluence-eligible by committed data but whose provisional W1 read now agrees with their committed D1 state.

#### Scenario: Asset with provisional confluence not yet committed
- **WHEN** an asset's committed W1 is BEARISH, committed D1 is BULLISH (not confluence-eligible today), and its provisional W1 read is BULLISH
- **THEN** the asset appears in the "Flips Forming" section

#### Scenario: Existing confirmed lists are unaffected
- **WHEN** the weekly-trend-briefing report is generated
- **THEN** the existing confluence and scan-candidate lists are populated and ranked using only committed data, with no provisional data included or affecting their order

### Requirement: BTC Alignment section includes BTC's provisional W1 read
The shared BTC Alignment section used by both weekly briefings SHALL include BTC's provisional W1 read alongside its existing committed W1 and D1 states.

#### Scenario: BTC provisional W1 diverges from committed W1
- **WHEN** BTC's committed W1 state is BEARISH but its provisional W1 read is BULLISH
- **THEN** the BTC Alignment section displays both the committed and provisional W1 states
