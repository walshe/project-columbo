## ADDED Requirements

### Requirement: Weekly briefing endpoints tolerate an asset class with no active assets
The system SHALL successfully compose a weekly briefing report (`POST /api/v1/weekly-trend-briefing` and `POST /api/v1/weekly-pullback-briefing`) even when one or more of the briefed asset classes (`CRYPTO`, `ETF`, `STOCK`) has zero active assets, rather than throwing an unhandled exception.

#### Scenario: An asset class with no active assets renders as "no snapshot yet"
- **WHEN** a briefed asset class has zero active assets (so no market breadth snapshot exists for it)
- **THEN** the report's regime-read section renders that asset class as "no snapshot yet" instead of the request failing

#### Scenario: A briefing request succeeds when only one asset class has active assets
- **WHEN** only `CRYPTO` has active assets and `ETF`/`STOCK` have none
- **THEN** both `POST /api/v1/weekly-trend-briefing` and `POST /api/v1/weekly-pullback-briefing` return 200 with a non-blank Markdown body
