// Phase 3: Signal State Detection Edge Function
// Port of Java SignalStateService.detectDaily() (SuperTrend only, RSI excluded)
// Detects trend state transitions for all active assets.
//
// Called by run-pipeline via HTTP — runs in its own isolate with a fresh CPU budget.
// Uses batch `.upsert()` with ON CONFLICT per asset for idempotent writes.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { getSupabaseClient } from "../_shared/supabase-client.ts";
import { calculate as calculateSignalStates } from "../_shared/signal-state-calculator.ts";
import { getFinalizedBoundary } from "../_shared/candle-filters.ts";
import type {
  Asset,
  SuperTrendDirection,
  SignalStateResult,
} from "../_shared/types.ts";

/**
 * Detects signal states for all active assets (SuperTrend only).
 * Port of Java SignalStateService.detectDaily().
 */
export async function detectSignals(): Promise<{
  inserted: number;
  updated: number;
  skipped: number;
  errors: number;
}> {
  const supabase = getSupabaseClient();
  const boundary = getFinalizedBoundary();

  const { data: assets, error: assetError } = await supabase
    .from("asset")
    .select("id, symbol, provider, active")
    .eq("active", true);

  if (assetError) throw new Error(`Failed to fetch assets: ${assetError.message}`);
  if (!assets || assets.length === 0) return { inserted: 0, updated: 0, skipped: 0, errors: 0 };

  let totalInserted = 0, totalUpdated = 0, totalSkipped = 0, totalErrors = 0;

  for (const asset of assets as Asset[]) {
    try {
      const stats = await processAsset(supabase, asset, boundary);
      totalInserted += stats.inserted;
      totalUpdated += stats.updated;
      totalSkipped += stats.skipped;
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      console.error(`Signal detection error for ${asset.symbol}: ${msg}`);
      totalErrors++;
    }
  }

  console.log(
    `Signal detection: ${totalInserted} inserted, ${totalUpdated} updated, ${totalSkipped} skipped, ${totalErrors} errors`
  );
  return { inserted: totalInserted, updated: totalUpdated, skipped: totalSkipped, errors: totalErrors };
}

async function processAsset(
  supabase: ReturnType<typeof getSupabaseClient>,
  asset: Asset,
  boundary: string
): Promise<{ inserted: number; updated: number; skipped: number }> {
  // Get the latest stored signal_state for this asset/SUPERTREND
  const { data: latestStored } = await supabase
    .from("signal_state")
    .select("close_time, trend_state, event")
    .eq("asset_id", asset.id)
    .eq("timeframe", "D1")
    .eq("indicator_type", "SUPERTREND")
    .order("close_time", { ascending: false })
    .limit(1)
    .single();

  // Fetch new SuperTrend indicators after the last stored signal
  let indicatorQuery = supabase
    .from("indicator_supertrend")
    .select("close_time, direction")
    .eq("asset_id", asset.id)
    .eq("timeframe", "D1")
    .lt("close_time", boundary)
    .order("close_time", { ascending: true });

  // For incremental mode, only fetch indicators after last stored close_time
  let previousDirection: SuperTrendDirection | null = null;
  if (latestStored) {
    indicatorQuery = indicatorQuery.gt("close_time", latestStored.close_time);

    // Get the direction of the last stored indicator to detect transitions
    const { data: lastIndicator } = await supabase
      .from("indicator_supertrend")
      .select("direction")
      .eq("asset_id", asset.id)
      .eq("timeframe", "D1")
      .eq("close_time", latestStored.close_time)
      .single();

    if (lastIndicator) {
      previousDirection = lastIndicator.direction as SuperTrendDirection;
    }
  }

  const { data: indicators, error: indError } = await indicatorQuery;
  if (indError) throw new Error(`Failed to fetch indicators: ${indError.message}`);

  // Calculate signal states
  const indicatorInputs = (indicators || []).map((ind: { close_time: string; direction: string }) => ({
    closeTime: ind.close_time,
    direction: ind.direction as SuperTrendDirection,
  }));

  const results = calculateSignalStates(indicatorInputs, previousDirection);

  // Handle empty results — port of Java SignalStateService lines 135-168
  if (results.length === 0) {
    return await handleNoResults(supabase, asset, boundary, latestStored);
  }

  // Batch upsert all signal state results at once
  const rows = results.map((result: SignalStateResult) => ({
    asset_id: asset.id,
    timeframe: "D1" as const,
    indicator_type: "SUPERTREND" as const,
    close_time: result.closeTime,
    trend_state: result.trendState,
    event: result.event,
  }));

  const { error } = await supabase
    .from("signal_state")
    .upsert(rows, { onConflict: "asset_id,timeframe,indicator_type,close_time" });

  if (error) throw new Error(`Failed to upsert signal states: ${error.message}`);

  if (results.length > 0) {
    console.log(`${asset.symbol}: signals ${results.length} upserted`);
  }
  return { inserted: results.length, updated: 0, skipped: 0 };
}

/**
 * Handles the case where no new indicator data exists.
 * Port of Java SignalStateService lines 135-168 (UNKNOWN state logic).
 */
async function handleNoResults(
  supabase: ReturnType<typeof getSupabaseClient>,
  asset: Asset,
  boundary: string,
  latestStored: { close_time: string; trend_state: string; event: string } | null
): Promise<{ inserted: number; updated: number; skipped: number }> {
  // Check if there's a finalized candle
  const { data: latestCandle } = await supabase
    .from("candle")
    .select("close_time")
    .eq("asset_id", asset.id)
    .eq("timeframe", "D1")
    .lt("close_time", boundary)
    .order("close_time", { ascending: false })
    .limit(1)
    .single();

  if (!latestCandle) return { inserted: 0, updated: 0, skipped: 0 };

  if (!latestStored) {
    // Case A: New asset with no signal — insert UNKNOWN
    const { error } = await supabase.from("signal_state").upsert({
      asset_id: asset.id,
      timeframe: "D1",
      indicator_type: "SUPERTREND",
      close_time: latestCandle.close_time,
      trend_state: "UNKNOWN",
      event: "NONE",
    }, { onConflict: "asset_id,timeframe,indicator_type,close_time" });
    if (error) throw new Error(`Failed to upsert UNKNOWN state: ${error.message}`);
    return { inserted: 1, updated: 0, skipped: 0 };
  }

  // Case B: Existing UNKNOWN state — update close_time if newer candle available
  if (
    latestStored.trend_state === "UNKNOWN" &&
    new Date(latestStored.close_time).getTime() < new Date(latestCandle.close_time).getTime()
  ) {
    const { error } = await supabase
      .from("signal_state")
      .update({ close_time: latestCandle.close_time })
      .eq("asset_id", asset.id)
      .eq("timeframe", "D1")
      .eq("indicator_type", "SUPERTREND")
      .eq("close_time", latestStored.close_time);
    if (error) throw new Error(`Failed to update UNKNOWN state: ${error.message}`);
    return { inserted: 0, updated: 1, skipped: 0 };
  }

  return { inserted: 0, updated: 0, skipped: 0 };
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
    const result = await detectSignals();
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
