// Phase 1: Candle Ingestion Edge Function
// Port of Java CandleIngestionService + CandlePersistenceService
// Fetches daily OHLCV candles from Binance for active assets and upserts them.
//
// ── Batching & performance ─────────────────────────────────────────────────────
//
// This function accepts `offset` and `limit` params to process a subset of assets.
// The run-pipeline orchestrator calls this in batches (default 10 assets per call)
// to stay within the Edge Runtime's ~15-second CPU time limit. Each batch runs in
// its own isolate with a fresh CPU budget.
//
// DB writes use batch `.upsert()` with ON CONFLICT instead of per-row
// SELECT+INSERT. This reduces DB round trips from ~2 per candle to 1 per asset,
// and ensures idempotency — safe to re-run without duplicates.
// ───────────────────────────────────────────────────────────────────────────────

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { getSupabaseClient } from "../_shared/supabase-client.ts";
import { fetchDailyCandlesWithDelay } from "../_shared/binance-client.ts";
import { getFinalizedBoundary } from "../_shared/candle-filters.ts";
import type { Asset, CandleDto, IngestionMetrics } from "../_shared/types.ts";

const BACKFILL_START_DEFAULT = "2025-11-11T00:00:00Z";

/**
 * Ingests daily candles for a batch of active assets.
 * @param offset - Start index into the asset list (default 0)
 * @param limit - Number of assets to process (default 10)
 * Returns ingestion metrics (inserted/updated/skipped/error counts).
 */
export async function ingestCandles(
  offset = 0,
  limit = 10
): Promise<IngestionMetrics> {
  const supabase = getSupabaseClient();
  const boundary = getFinalizedBoundary();
  const boundaryMs = new Date(boundary).getTime();

  // Fetch active assets with pagination
  const { data: assets, error: assetError } = await supabase
    .from("asset")
    .select("id, symbol, provider, active")
    .eq("active", true)
    .order("id", { ascending: true })
    .range(offset, offset + limit - 1);

  if (assetError) throw new Error(`Failed to fetch assets: ${assetError.message}`);
  if (!assets || assets.length === 0) {
    return { assetCount: 0, insertedCount: 0, updatedCount: 0, skippedCount: 0, errorCount: 0, errorSample: null };
  }

  const metrics: IngestionMetrics = {
    assetCount: assets.length,
    insertedCount: 0,
    updatedCount: 0,
    skippedCount: 0,
    errorCount: 0,
    errorSample: null,
  };

  const backfillStart = Deno.env.get("BACKFILL_START") || BACKFILL_START_DEFAULT;
  const backfillStartMs = new Date(backfillStart).getTime();

  for (const asset of assets as Asset[]) {
    try {
      const assetMetrics = await ingestForAsset(supabase, asset, boundaryMs, backfillStartMs);
      metrics.insertedCount += assetMetrics.insertedCount;
      metrics.updatedCount += assetMetrics.updatedCount;
      metrics.skippedCount += assetMetrics.skippedCount;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      console.error(`Failed to ingest ${asset.symbol}: ${msg}`);
      metrics.errorCount++;
      if (metrics.errorSample === null) {
        metrics.errorSample = `${asset.symbol}: ${msg}`;
      }
    }
  }

  console.log(
    `Ingestion batch (offset=${offset}, limit=${limit}): ${metrics.insertedCount} inserted, ` +
    `${metrics.updatedCount} updated, ${metrics.skippedCount} skipped, ${metrics.errorCount} errors`
  );
  return metrics;
}

async function ingestForAsset(
  supabase: ReturnType<typeof getSupabaseClient>,
  asset: Asset,
  boundaryMs: number,
  backfillStartMs: number
): Promise<{ insertedCount: number; updatedCount: number; skippedCount: number }> {
  // Get last candle close_time for this asset
  const { data: lastCandle } = await supabase
    .from("candle")
    .select("close_time")
    .eq("asset_id", asset.id)
    .eq("timeframe", "D1")
    .order("close_time", { ascending: false })
    .limit(1)
    .single();

  // Compute time window
  const startTimeMs = lastCandle
    ? new Date(lastCandle.close_time).getTime() + 1
    : backfillStartMs;
  const endTimeMs = boundaryMs;

  // Skip if already up to date
  if (startTimeMs >= endTimeMs) {
    console.log(`${asset.symbol}: up to date, skipping`);
    return { insertedCount: 0, updatedCount: 0, skippedCount: 0 };
  }

  console.log(`${asset.symbol}: fetching candles from ${startTimeMs} (${new Date(startTimeMs).toISOString()}) to ${endTimeMs} (${new Date(endTimeMs).toISOString()})`);

  // Fetch from Binance (includes 200ms delay)
  const candles = await fetchDailyCandlesWithDelay(asset.symbol, startTimeMs, endTimeMs);

  // Filter finalized only (close_time < boundary)
  const finalized = candles.filter(
    (c) => new Date(c.closeTime).getTime() < boundaryMs
  );

  if (finalized.length === 0) {
    console.log(`${asset.symbol}: no finalized candles to persist`);
    return { insertedCount: 0, updatedCount: 0, skippedCount: 0 };
  }

  // Batch upsert all candles at once using ON CONFLICT
  const rows = finalized.map((candle: CandleDto) => ({
    asset_id: asset.id,
    timeframe: "D1" as const,
    open_time: candle.openTime,
    close_time: candle.closeTime,
    open: candle.open,
    high: candle.high,
    low: candle.low,
    close: candle.close,
    volume: candle.volume,
    source: "BINANCE" as const,
  }));

  const { error } = await supabase
    .from("candle")
    .upsert(rows, { onConflict: "asset_id,timeframe,close_time" });

  if (error) throw new Error(`Failed to upsert candles: ${error.message}`);

  const count = finalized.length;
  console.log(`${asset.symbol}: ${count} upserted`);
  return { insertedCount: count, updatedCount: 0, skippedCount: 0 };
}

// HTTP handler for standalone invocation
Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed" }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }
    const body = await req.json().catch(() => ({}));
    const offset = body.offset ?? 0;
    const limit = body.limit ?? 10;
    const metrics = await ingestCandles(offset, limit);
    return new Response(JSON.stringify(metrics), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error(`ingest-candles error: ${msg}`);
    return new Response(JSON.stringify({ error: msg }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
