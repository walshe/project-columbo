-- Seed initial tradable assets

INSERT INTO asset (symbol, provider, active, created_at)
VALUES 
  ('BTC', 'BINANCE', true, now()),
  ('ETH', 'BINANCE', true, now()),
  ('SOL', 'BINANCE', true, now())
ON CONFLICT (symbol, provider) DO NOTHING;
