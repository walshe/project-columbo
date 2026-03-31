// Phase 4: Market Pulse Aggregation Edge Function
// Port of Java MarketPulseService.computeDaily() (SuperTrend only)
// Aggregates bullish/bearish/missing counts across all active assets.
//
// Called by run-pipeline via HTTP — runs in its own isolate with a fresh CPU budget.
// This is the lightest phase — just a single RPC call + one upsert.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { getSupabaseClient } from "../_shared/supabase-client.ts";
import { getFinalizedBoundary } from "../_shared/candle-filters.ts";
import { d, div } from "../_shared/decimal.ts";

/**
 * Computes market breadth snapshot for SUPERTREND indicator.
 * Port of Java MarketPulseService.computePulseForIndicator().
 */
export async function computeMarketPulse(): Promise<{
  snapshotCloseTime: string | null;
  bullishCount: number;
  bearishCount: number;
  missingCount: number;
  totalAssets: number;
  bullishRatio: string;
}> {
  const supabase = getSupabaseClient();
  const boundary = getFinalizedBoundary();

  // Count active assets
  const { count: totalActiveAssets, error: countError } = await supabase
    .from("asset")
    .select("id", { count: "exact", head: true })
    .eq("active", true);

  if (countError) throw new Error(`Failed to count assets: ${countError.message}`);
  if (!totalActiveAssets || totalActiveAssets === 0) {
    console.warn("No active assets for MarketPulse");
    return { snapshotCloseTime: null, bullishCount: 0, bearishCount: 0, missingCount: 0, totalAssets: 0, bullishRatio: "0" };
  }

  // Fetch latest finalized signal states per asset (SUPERTREND)
  // Using DISTINCT ON to get latest per asset
  const { data: latestStates, error: stateError } = await supabase
    .rpc("get_signals", {
      p_timeframe: "D1",
      p_indicator_type: "SUPERTREND",
      p_state_filter: null,
      p_sort: "ASSET_ASC",
    });

  if (stateError) throw new Error(`Failed to fetch signal states: ${stateError.message}`);
  if (!latestStates || latestStates.length === 0) {
    console.log("No signal states found for MarketPulse");
    return { snapshotCloseTime: null, bullishCount: 0, bearishCount: 0, missingCount: 0, totalAssets: totalActiveAssets, bullishRatio: "0" };
  }

  // Find the latest close_time across all signals
  const latestCloseTime = latestStates.reduce(
    (max: string, s: { close_time: string }) =>
      s.close_time > max ? s.close_time : max,
    latestStates[0].close_time
  );

  // Count by state
  // Note: get_signals returns trend_state, not all states may be at the same close_time
  // Filter to only those at the latest close_time for consistency
  const statesAtTime = latestStates.filter(
    (s: { close_time: string }) => s.close_time === latestCloseTime
  );

  const bullishCount = statesAtTime.filter(
    (s: { trend_state: string }) => s.trend_state === "BULLISH"
  ).length;
  const bearishCount = statesAtTime.filter(
    (s: { trend_state: string }) => s.trend_state === "BEARISH"
  ).length;
  const unknownCount = statesAtTime.filter(
    (s: { trend_state: string }) => s.trend_state === "UNKNOWN"
  ).length;
  const missingCount = totalActiveAssets - bullishCount - bearishCount - unknownCount;

  // Compute bullish ratio = bullish / (bullish + bearish), matching Java SCALE=4
  const presentCount = bullishCount + bearishCount;
  const bullishRatio =
    presentCount > 0
      ? div(d(bullishCount), d(presentCount)).toFixed(4)
      : "0.0000";

  // Upsert snapshot
  const { data: existing } = await supabase
    .from("market_breadth_snapshot")
    .select("id, bullish_count, bearish_count, missing_count, total_assets, bullish_ratio")
    .eq("timeframe", "D1")
    .eq("indicator_type", "SUPERTREND")
    .eq("snapshot_close_time", latestCloseTime)
    .single();

  if (existing) {
    // Check if identical
    if (
      existing.bullish_count === bullishCount &&
      existing.bearish_count === bearishCount &&
      existing.missing_count === missingCount &&
      existing.total_assets === totalActiveAssets &&
      existing.bullish_ratio === bullishRatio
    ) {
      console.log(`MarketPulse snapshot already exists and is identical for ${latestCloseTime}`);
    } else {
      console.warn(`REVISION: MarketPulse snapshot changed for ${latestCloseTime}. Updating.`);
      const { error } = await supabase
        .from("market_breadth_snapshot")
        .update({
          bullish_count: bullishCount,
          bearish_count: bearishCount,
          missing_count: missingCount,
          total_assets: totalActiveAssets,
          bullish_ratio: bullishRatio,
        })
        .eq("id", existing.id);
      if (error) throw new Error(`Failed to update snapshot: ${error.message}`);
    }
  } else {
    const { error } = await supabase.from("market_breadth_snapshot").insert({
      timeframe: "D1",
      indicator_type: "SUPERTREND",
      snapshot_close_time: latestCloseTime,
      bullish_count: bullishCount,
      bearish_count: bearishCount,
      missing_count: missingCount,
      total_assets: totalActiveAssets,
      bullish_ratio: bullishRatio,
    });
    if (error) throw new Error(`Failed to insert snapshot: ${error.message}`);
    console.log(`Created new MarketPulse snapshot for ${latestCloseTime}`);
  }

  return {
    snapshotCloseTime: latestCloseTime,
    bullishCount,
    bearishCount,
    missingCount,
    totalAssets: totalActiveAssets,
    bullishRatio,
  };
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
    const result = await computeMarketPulse();
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
