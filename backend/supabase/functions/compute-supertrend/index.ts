// Phase 2: SuperTrend Indicator Computation Edge Function
// Port of Java SuperTrendService.processAllActiveAssets()
// Computes SuperTrend(10, 2.0) for all active assets incrementally.
//
// Called by run-pipeline via HTTP — runs in its own isolate with a fresh CPU budget.
// Uses batch `.upsert()` with ON CONFLICT per asset for idempotent writes.
// The computation itself (calculateIncremental) is CPU-intensive but operates
// on in-memory data already fetched from the DB, so it completes quickly.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { getSupabaseClient } from "../_shared/supabase-client.ts";
import { calculateIncremental } from "../_shared/supertrend-calculator.ts";
import { getFinalizedBoundary } from "../_shared/candle-filters.ts";
import type { Asset, Candle, SuperTrendResult } from "../_shared/types.ts";

const ATR_LENGTH = 10;
const MULTIPLIER = "2.0";

/**
 * Computes SuperTrend for all active assets incrementally.
 * Port of Java SuperTrendService.processAllActiveAssets().
 */
export async function computeSupertrend(): Promise<{
  processed: number;
  errors: number;
}> {
  const supabase = getSupabaseClient();
  const boundary = getFinalizedBoundary();

  const { data: assets, error: assetError } = await supabase
    .from("asset")
    .select("id, symbol, provider, active")
    .eq("active", true);

  if (assetError) throw new Error(`Failed to fetch assets: ${assetError.message}`);
  if (!assets || assets.length === 0) return { processed: 0, errors: 0 };

  let processed = 0;
  let errors = 0;

  for (const asset of assets as Asset[]) {
    try {
      await processAsset(supabase, asset, boundary);
      processed++;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      console.error(`SuperTrend error for ${asset.symbol}: ${msg}`);
      errors++;
    }
  }

  console.log(`SuperTrend computation: ${processed} processed, ${errors} errors`);
  return { processed, errors };
}

async function processAsset(
  supabase: ReturnType<typeof getSupabaseClient>,
  asset: Asset,
  boundary: string
): Promise<void> {
  // Get last stored indicator close_time
  const { data: lastIndicator } = await supabase
    .from("indicator_supertrend")
    .select("close_time")
    .eq("asset_id", asset.id)
    .eq("timeframe", "D1")
    .order("close_time", { ascending: false })
    .limit(1)
    .single();

  const lastStoredCloseTime = lastIndicator?.close_time ?? null;

  // Fetch all finalized candles for this asset (needed for full calculation)
  const { data: candles, error: candleError } = await supabase
    .from("candle")
    .select("close_time, high, low, close")
    .eq("asset_id", asset.id)
    .eq("timeframe", "D1")
    .lt("close_time", boundary)
    .order("close_time", { ascending: true });

  if (candleError) throw new Error(`Failed to fetch candles: ${candleError.message}`);
  if (!candles || candles.length === 0) return;

  // Map to calculator input format
  const candleInputs = candles.map((c: Candle) => ({
    closeTime: c.close_time,
    high: c.high,
    low: c.low,
    close: c.close,
  }));

  // Calculate incrementally
  const results = calculateIncremental(
    candleInputs,
    ATR_LENGTH,
    MULTIPLIER,
    lastStoredCloseTime,
    false // incremental mode
  );

  if (results.length === 0) {
    console.log(`${asset.symbol}: SuperTrend up to date`);
    return;
  }

  // Batch upsert all results at once
  const rows = results.map((result: SuperTrendResult) => ({
    asset_id: asset.id,
    timeframe: "D1" as const,
    close_time: result.closeTime,
    atr: result.atr,
    upper_band: result.upperBand,
    lower_band: result.lowerBand,
    supertrend: result.supertrend,
    direction: result.direction,
  }));

  const { error } = await supabase
    .from("indicator_supertrend")
    .upsert(rows, { onConflict: "asset_id,timeframe,close_time" });

  if (error) throw new Error(`Failed to upsert indicators: ${error.message}`);

  console.log(`${asset.symbol}: SuperTrend ${results.length} upserted`);
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
    const result = await computeSupertrend();
    return new Response(JSON.stringify(result), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    return new Response(JSON.stringify({ error: msg }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
