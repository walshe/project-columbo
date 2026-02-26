
- find out what limits are from Binance
- The more pressing issue I'd flag is actually the @Transactional on ingestForAsset — that keeps a DB transaction 
- open for the entire duration including the Binance HTTP call, which is risky
- maybe get rid of MarketPulse and it is based on SuperTrend only
- use Guava's RateLimiter in BinanceMarketDataProvider insstead of sleep in CandleIngestionService
- add a backtester ?
- add an ATR indicator?
- add a GAP indicator ? (finds assets that gapped overnight/weekend)