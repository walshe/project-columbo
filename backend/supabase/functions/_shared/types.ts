// Shared type definitions for the Supabase Edge Functions
// Mirrors Java enums and entity models (RSI excluded)

export type Timeframe = "D1";
export type Provider = "BINANCE";
export type SuperTrendDirection = "UP" | "DOWN";
export type IndicatorType = "SUPERTREND";
export type TrendState = "BULLISH" | "BEARISH" | "UNKNOWN";
export type SignalEvent = "NONE" | "BULLISH_REVERSAL" | "BEARISH_REVERSAL";
export type IngestionRunStatus = "RUNNING" | "SUCCESS" | "PARTIAL" | "FAILED";

// ── Database row types ──────────────────────────────────────────────────────

export interface Asset {
  id: number;
  symbol: string;
  name: string | null;
  provider: Provider;
  active: boolean;
  created_at: string;
}

export interface Candle {
  id: number;
  asset_id: number;
  timeframe: Timeframe;
  open_time: string;
  close_time: string;
  open: string; // NUMERIC comes back as string from Supabase
  high: string;
  low: string;
  close: string;
  volume: string;
  source: Provider;
  raw_payload: Record<string, unknown> | null;
  created_at: string;
}

export interface SuperTrendRow {
  id: number;
  asset_id: number;
  timeframe: Timeframe;
  close_time: string;
  atr: string;
  upper_band: string;
  lower_band: string;
  supertrend: string;
  direction: SuperTrendDirection;
  created_at: string;
}

export interface SignalStateRow {
  id: number;
  asset_id: number;
  timeframe: Timeframe;
  indicator_type: IndicatorType;
  close_time: string;
  trend_state: TrendState;
  event: SignalEvent;
  created_at: string;
}

export interface MarketBreadthSnapshotRow {
  id: number;
  timeframe: Timeframe;
  indicator_type: IndicatorType;
  snapshot_close_time: string;
  bullish_count: number;
  bearish_count: number;
  missing_count: number;
  total_assets: number;
  bullish_ratio: string;
  created_at: string;
}

export interface IngestionRunRow {
  id: number;
  provider: Provider;
  timeframe: Timeframe;
  started_at: string;
  finished_at: string | null;
  duration_ms: number | null;
  status: IngestionRunStatus;
  asset_count: number;
  inserted_count: number;
  updated_count: number;
  skipped_count: number;
  error_count: number;
  error_sample: string | null;
  created_at: string;
}

// ── Computation types ───────────────────────────────────────────────────────

export interface CandleDto {
  open: string;
  high: string;
  low: string;
  close: string;
  volume: string;
  openTime: string; // ISO timestamp
  closeTime: string; // ISO timestamp
}

export interface SuperTrendResult {
  closeTime: string;
  atr: string;
  upperBand: string;
  lowerBand: string;
  supertrend: string;
  direction: SuperTrendDirection;
}

export interface SignalStateResult {
  closeTime: string;
  trendState: TrendState;
  event: SignalEvent;
}

// ── Ingestion metrics ───────────────────────────────────────────────────────

export interface IngestionMetrics {
  assetCount: number;
  insertedCount: number;
  updatedCount: number;
  skippedCount: number;
  errorCount: number;
  errorSample: string | null;
}

// ── API response types ──────────────────────────────────────────────────────

export interface SignalDto {
  symbol: string;
  trendState: TrendState;
  event: SignalEvent;
  closeTime: string;
  lastFlipTime: string | null;
  daysSinceFlip: number | null;
  avgVolume7d: number;
  tradingviewUrl: string;
}

export interface MarketPulseDto {
  snapshotCloseTime: string;
  bullishCount: number;
  bearishCount: number;
  missingCount: number;
  totalAssets: number;
  bullishRatio: number;
}
