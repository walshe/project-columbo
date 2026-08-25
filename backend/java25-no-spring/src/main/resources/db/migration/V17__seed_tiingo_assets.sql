-- 47 real-equity assets sourced from Tiingo, additive alongside the existing tokenized Binance
-- STOCK assets (V12) for the same companies - both stay active, see
-- openspec/changes/add-tiingo-provider/design.md's coexistence decision. venue and name are set
-- explicitly on every row rather than relying on column defaults, unlike V12's original mistake
-- (see V14's fix) of relying on venue's DEFAULT 'SPOT'.
--
-- SSNLF/TCEHY/RHHBY are US OTC ADRs (exchangeCode PINK on Tiingo), substituted for Samsung
-- Electronics/Tencent/Roche's non-US primary listings, which Tiingo doesn't carry. 601398/601288/
-- 601939 are Shanghai-listed China A-shares that Tiingo does carry directly (exchangeCode SHG).
INSERT INTO asset (symbol, provider, active, asset_class, venue, name, created_at)
VALUES
    ('NVDA', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'NVIDIA Corporation', now()),
    ('AAPL', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Apple Inc', now()),
    ('GOOG', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Alphabet Inc', now()),
    ('MSFT', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Microsoft Corporation', now()),
    ('AMZN', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Amazon.com Inc', now()),
    ('TSM', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Taiwan Semiconductor Manufacturing Company Limited', now()),
    ('AVGO', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Broadcom Inc', now()),
    ('TSLA', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Tesla Inc', now()),
    ('META', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Meta Platforms Inc', now()),
    ('SSNLF', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Samsung Electronics Co Ltd', now()),
    ('LLY', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Eli Lilly and Company', now()),
    ('MU', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Micron Technology Inc', now()),
    ('BRK-A', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Berkshire Hathaway Inc', now()),
    ('JPM', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'JPMorgan Chase & Co', now()),
    ('WMT', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Walmart Inc', now()),
    ('AMD', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Advanced Micro Devices Inc', now()),
    ('V', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Visa Inc', now()),
    ('XOM', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Exxon Mobil Corporation', now()),
    ('ASML', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'ASML Holding NV', now()),
    ('JNJ', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Johnson & Johnson', now()),
    ('TCEHY', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Tencent Holdings Ltd', now()),
    ('MA', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Mastercard Incorporated', now()),
    ('INTC', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Intel Corporation', now()),
    ('ABBV', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'AbbVie Inc', now()),
    ('CSCO', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Cisco Systems Inc', now()),
    ('PLTR', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Palantir Technologies Inc', now()),
    ('BAC', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Bank of America Corporation', now()),
    ('ORCL', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Oracle Corporation', now()),
    ('COST', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Costco Wholesale Corporation', now()),
    ('CVX', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Chevron Corporation', now()),
    ('601398', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Industrial and Commercial Bank of China Limited', now()),
    ('LRCX', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Lam Research Corporation', now()),
    ('KO', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'The Coca-Cola Company', now()),
    ('AMAT', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Applied Materials Inc', now()),
    ('CAT', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Caterpillar Inc', now()),
    ('MRK', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Merck & Co Inc', now()),
    ('RHHBY', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Roche Holding AG', now()),
    ('GE', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'GE Aerospace', now()),
    ('HSBC', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'HSBC Holdings plc', now()),
    ('UNH', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'UnitedHealth Group Incorporated', now()),
    ('601288', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Agricultural Bank of China Limited', now()),
    ('MS', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Morgan Stanley', now()),
    ('PG', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Procter & Gamble Company', now()),
    ('HD', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'The Home Depot Inc', now()),
    ('NFLX', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'Netflix Inc', now()),
    ('601939', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'China Construction Bank Corporation', now()),
    ('GS', 'TIINGO', true, 'STOCK', 'EXCHANGE', 'The Goldman Sachs Group Inc', now())
ON CONFLICT (symbol, provider) DO NOTHING;
