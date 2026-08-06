# Future retrieval context (notes, not a design)

This system has no news, fundamentals, or narrative data source today - every field in the
prompts in this folder comes straight from this module's own Postgres-backed API (candles,
indicators, signal state, market breadth). These are just ideas for what retrieval *could* add
later, worth having on hand when this becomes a real OpenSpec proposal - not a commitment to
build any of them.

## Ideas, roughly ordered by how cheaply they'd fit this system's existing shape

1. **This asset's own flip history.** `signal_state` already stores every past flip per
   asset+timeframe - a query (not a new data source) could surface "this asset has flipped N
   times in the last 90 days" as a noisiness signal, distinguishing a genuinely fresh trend
   from a choppy asset that flips every week. Zero new ingestion required.

2. **Asset class / sector context.** `AssetClass` (crypto/stock/etf/commodity) already exists;
   a small static lookup (sector, or "why this symbol is on Binance at all" for the stock/ETF
   perpetuals) would let the digest say something like "one of several semiconductor names
   flipping bullish today" instead of treating every symbol as an isolated data point. Doesn't
   need an external API - could be a seed table alongside the asset seed migrations.

3. **Cross-asset correlation within a digest batch.** If multiple scan matches in the same
   batch share an asset class or a recent flip date, that's a pattern worth surfacing (e.g. "6
   of today's 8 matches are STOCK-class, all flipped within 2 days of each other") - computable
   from data already in the digest's own input, no retrieval needed at all, just a prompt/logic
   change to `daily-digest.md`.

4. **External news/headlines (real retrieval, real new dependency).** The obvious "why is this
   moving" context this system can't currently answer at all. Would need: a news source
   subscription/API, a way to match headlines to a symbol (ticker vs. crypto symbol
   disambiguation is not trivial - e.g. is "BEUSDT" always going to disambiguate cleanly?), and
   a decision about staleness/caching. This is the most valuable addition and also the most
   expensive to build and get wrong - the deliberate reason it's ranked last.

## What NOT to add regardless of source

Whatever retrieval eventually gets built, it should feed the *narrative/ranking* layer only -
never a source for support/resistance levels or price targets. That constraint isn't a
retrieval-quality problem to solve with better data; it's a deliberate scope boundary (see
`README.md`) that should survive any future retrieval work.
