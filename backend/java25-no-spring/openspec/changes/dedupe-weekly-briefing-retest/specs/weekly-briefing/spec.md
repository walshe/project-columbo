## ADDED Requirements

### Requirement: Each weekly briefing endpoint owns a distinct headline thesis with no duplicated candidate lists
`POST /api/v1/weekly-trend-briefing` and `POST /api/v1/weekly-pullback-briefing` SHALL NOT render the same underlying candidate list under different section titles. The trend briefing SHALL headline confluence (W1 and D1 currently aligned) and SHALL NOT render retest candidates. The pullback briefing SHALL remain the sole endpoint reporting retest candidates (W1 aligned, D1 recently counter-flipped).

#### Scenario: The trend briefing does not render retest candidates
- **WHEN** `POST /api/v1/weekly-trend-briefing` composes its report for an asset class
- **THEN** the response contains a "Confluence" section for that class but no "Retest" section

#### Scenario: The pullback briefing is the sole source of retest candidates
- **WHEN** a caller wants assets whose W1 trend is intact but whose D1 has recently flipped counter to it
- **THEN** `POST /api/v1/weekly-pullback-briefing` is the endpoint that reports them, with its `watch:` provisional-divergence annotations intact
