-- Verified TradingView EXCHANGE:SYMBOL references for the 47 V17-seeded Tiingo assets, confirmed
-- individually against TradingView's own symbol search (not guessed from Tiingo's exchangeCode
-- metadata, which uses different exchange labels and doesn't always match TradingView's own
-- ticker format - e.g. Tiingo's "BRK-A" is TradingView's "BRK.A"). SSNLF/TCEHY/RHHBY (OTC ADRs)
-- and 601398/601288/601939 (Shanghai A-shares, exchange "SSE") don't follow the NASDAQ/NYSE
-- pattern of the other 41 - see openspec/changes/add-tradingview-exchange-ref/ for the full
-- per-ticker verification.
UPDATE asset AS a
SET tradingview_ref = v.tradingview_ref
FROM (VALUES
    ('NVDA', 'NASDAQ:NVDA'),
    ('AAPL', 'NASDAQ:AAPL'),
    ('GOOG', 'NASDAQ:GOOG'),
    ('MSFT', 'NASDAQ:MSFT'),
    ('AMZN', 'NASDAQ:AMZN'),
    ('TSM', 'NYSE:TSM'),
    ('AVGO', 'NASDAQ:AVGO'),
    ('TSLA', 'NASDAQ:TSLA'),
    ('META', 'NASDAQ:META'),
    ('SSNLF', 'OTC:SSNLF'),
    ('LLY', 'NYSE:LLY'),
    ('MU', 'NASDAQ:MU'),
    ('BRK-A', 'NYSE:BRK.A'),
    ('JPM', 'NYSE:JPM'),
    ('WMT', 'NASDAQ:WMT'),
    ('AMD', 'NASDAQ:AMD'),
    ('V', 'NYSE:V'),
    ('XOM', 'NYSE:XOM'),
    ('ASML', 'NASDAQ:ASML'),
    ('JNJ', 'NYSE:JNJ'),
    ('TCEHY', 'OTC:TCEHY'),
    ('MA', 'NYSE:MA'),
    ('INTC', 'NASDAQ:INTC'),
    ('ABBV', 'NYSE:ABBV'),
    ('CSCO', 'NASDAQ:CSCO'),
    ('PLTR', 'NASDAQ:PLTR'),
    ('BAC', 'NYSE:BAC'),
    ('ORCL', 'NYSE:ORCL'),
    ('COST', 'NASDAQ:COST'),
    ('CVX', 'NYSE:CVX'),
    ('601398', 'SSE:601398'),
    ('LRCX', 'NASDAQ:LRCX'),
    ('KO', 'NYSE:KO'),
    ('AMAT', 'NASDAQ:AMAT'),
    ('CAT', 'NYSE:CAT'),
    ('MRK', 'NYSE:MRK'),
    ('RHHBY', 'OTC:RHHBY'),
    ('GE', 'NYSE:GE'),
    ('HSBC', 'NYSE:HSBC'),
    ('UNH', 'NYSE:UNH'),
    ('601288', 'SSE:601288'),
    ('MS', 'NYSE:MS'),
    ('PG', 'NYSE:PG'),
    ('HD', 'NYSE:HD'),
    ('NFLX', 'NASDAQ:NFLX'),
    ('601939', 'SSE:601939'),
    ('GS', 'NYSE:GS')
) AS v(symbol, tradingview_ref)
WHERE a.symbol = v.symbol AND a.provider = 'TIINGO';
