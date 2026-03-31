// Pipeline Orchestrator Edge Function
// Port of Java MarketPipelineService.runDaily()
//
// ── Architecture: HTTP-based phase delegation ──────────────────────────────────
//
// Supabase Edge Runtime enforces a ~15-second CPU time limit per isolate.
// Processing 60 assets (Binance API calls + DB writes) in a single isolate
// would exceed this limit. To work around it, this orchestrator delegates each
// pipeline phase to a **separate Edge Function via HTTP fetch**:
//
//   trigger-pipeline → HTTP → run-pipeline (this file)
//                                → HTTP → ingest-candles (batch 1 of N)
//                                → HTTP → ingest-candles (batch 2 of N)
//                                → ...
//                                → HTTP → compute-supertrend
//                                → HTTP → detect-signals
//                                → HTTP → compute-market-pulse
//
// Each HTTP call spins up a new isolate with a fresh CPU budget. The orchestrator
// itself uses almost zero CPU — it's mostly `await fetch()` which is I/O wait,
// not CPU time. Edge Runtime limits CPU time, not wall-clock time.
//
// Ingestion is further split into batches of INGESTION_BATCH_SIZE assets per
// call, since each asset requires a Binance API request (~200ms) plus DB writes.
//
// ── Transactional trade-offs ───────────────────────────────────────────────────
//
// Because each phase (and each ingestion batch) runs in a separate HTTP request,
// there is no all-or-nothing transaction across the full pipeline. If batch 3 of
// ingestion fails, batches 1-2 are already committed. If compute-supertrend fails,
// ingested candles remain in the DB.
//
// This is acceptable because:
// 1. All writes are idempotent — every upsert uses ON CONFLICT, so re-running
//    the pipeline safely picks up where it left off.
// 2. The Java backend also isn't fully transactional — it processes per-asset,
//    catches errors individually, and reports PARTIAL status.
// 3. The ingestion_run table tracks overall status (SUCCESS/PARTIAL/FAILED)
//    with error counts, so partial runs are visible and diagnosable.
// ───────────────────────────────────────────────────────────────────────────────

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { getSupabaseClient } from "../_shared/supabase-client.ts";
import {
  assertNoRunningPipeline,
  createRunningRecord,
  finalizeRun,
} from "../_shared/pipeline-lock.ts";
import type { Provider, Timeframe } from "../_shared/types.ts";

export interface PipelineResult {
  runId: number;
  status: string;
  phases: {
    ingestion?: Record<string, unknown>;
    indicator?: Record<string, unknown>;
    signal?: Record<string, unknown>;
    marketPulse?: Record<string, unknown>;
  };
  durationMs: number;
}

const INGESTION_BATCH_SIZE = 10;

/**
 * Calls a sibling Edge Function via HTTP.
 * Each phase runs in its own isolate with a fresh CPU time budget.
 */
async function callPhase(
  functionName: string,
  body: Record<string, unknown> = {}
): Promise<Record<string, unknown>> {
  const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
  const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
  const url = `${supabaseUrl}/functions/v1/${functionName}`;

  const response = await fetch(url, {
    method: "POST",
    headers: {
      "Authorization": `Bearer ${serviceRoleKey}`,
      "Content-Type": "application/json",
    },
    body: JSON.stringify(body),
  });

  const result = await response.json();
  if (!response.ok) {
    throw new Error(`Phase ${functionName} failed (${response.status}): ${JSON.stringify(result)}`);
  }
  return result;
}

/**
 * Runs the full daily market pipeline.
 * Port of Java MarketPipelineService.runDaily().
 */
export async function runPipeline(
  provider: Provider = "BINANCE",
  timeframe: Timeframe = "D1"
): Promise<PipelineResult> {
  const supabase = getSupabaseClient();
  const startedAt = new Date();

  // 1. Concurrency check
  await assertNoRunningPipeline(supabase, provider, timeframe);

  // 2. Count active assets
  const { count: assetCount } = await supabase
    .from("asset")
    .select("id", { count: "exact", head: true })
    .eq("active", true);

  const totalAssets = assetCount || 0;

  // 3. Create RUNNING record
  const runId = await createRunningRecord(
    supabase,
    provider,
    timeframe,
    totalAssets
  );

  console.log(`Pipeline started: run ${runId} for ${provider} ${timeframe} (${totalAssets} assets)`);

  const phases: PipelineResult["phases"] = {};
  let totalInserted = 0, totalUpdated = 0, totalSkipped = 0, totalErrors = 0;
  let errorSample: string | null = null;

  try {
    // PHASE 1: INGESTION — call in batches of INGESTION_BATCH_SIZE
    console.log("Starting phase: INGESTION");
    const ingestionStart = Date.now();
    const ingestionAgg = { insertedCount: 0, updatedCount: 0, skippedCount: 0, errorCount: 0 };

    for (let offset = 0; offset < totalAssets; offset += INGESTION_BATCH_SIZE) {
      console.log(`  Ingestion batch: offset=${offset}, limit=${INGESTION_BATCH_SIZE}`);
      const batchResult = await callPhase("ingest-candles", {
        offset,
        limit: INGESTION_BATCH_SIZE,
      });
      ingestionAgg.insertedCount += (batchResult.insertedCount as number) || 0;
      ingestionAgg.updatedCount += (batchResult.updatedCount as number) || 0;
      ingestionAgg.skippedCount += (batchResult.skippedCount as number) || 0;
      ingestionAgg.errorCount += (batchResult.errorCount as number) || 0;
      if (batchResult.errorSample && !errorSample) {
        errorSample = batchResult.errorSample as string;
      }
    }

    console.log(`Completed phase: INGESTION in ${Date.now() - ingestionStart}ms`);
    phases.ingestion = ingestionAgg;
    totalInserted += ingestionAgg.insertedCount;
    totalUpdated += ingestionAgg.updatedCount;
    totalSkipped += ingestionAgg.skippedCount;
    totalErrors += ingestionAgg.errorCount;

    // PHASE 2: INDICATOR COMPUTATION — call as separate Edge Function
    console.log("Starting phase: INDICATOR");
    const indicatorStart = Date.now();
    const indicatorResult = await callPhase("compute-supertrend");
    console.log(`Completed phase: INDICATOR in ${Date.now() - indicatorStart}ms`);
    phases.indicator = indicatorResult;
    totalErrors += (indicatorResult.errors as number) || 0;

    // PHASE 3: SIGNAL DETECTION — call as separate Edge Function
    console.log("Starting phase: SIGNAL");
    const signalStart = Date.now();
    const signalResult = await callPhase("detect-signals");
    console.log(`Completed phase: SIGNAL in ${Date.now() - signalStart}ms`);
    phases.signal = signalResult;
    totalErrors += (signalResult.errors as number) || 0;

    // PHASE 4: MARKET PULSE — call as separate Edge Function
    console.log("Starting phase: MARKET_PULSE");
    const pulseStart = Date.now();
    const pulseResult = await callPhase("compute-market-pulse");
    console.log(`Completed phase: MARKET_PULSE in ${Date.now() - pulseStart}ms`);
    phases.marketPulse = pulseResult;

    // Finalize run
    await finalizeRun(supabase, runId, {
      insertedCount: totalInserted,
      updatedCount: totalUpdated,
      skippedCount: totalSkipped,
      errorCount: totalErrors,
      errorSample,
    }, startedAt);

    const durationMs = Date.now() - startedAt.getTime();
    console.log(`Pipeline completed: run ${runId} in ${durationMs}ms`);

    return { runId, status: totalErrors === 0 ? "SUCCESS" : "PARTIAL", phases, durationMs };
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error(`Pipeline failed at phase: ${msg}`);

    await finalizeRun(supabase, runId, {
      insertedCount: totalInserted,
      updatedCount: totalUpdated,
      skippedCount: totalSkipped,
      errorCount: totalErrors + 1,
      errorSample: errorSample || msg,
    }, startedAt);

    const durationMs = Date.now() - startedAt.getTime();
    return { runId, status: "FAILED", phases, durationMs };
  }
}

// HTTP handler (mainly used by trigger-pipeline, but also works standalone)
Deno.serve(async (req) => {
  try {
    if (req.method !== "POST") {
      return new Response(JSON.stringify({ error: "Method not allowed" }), {
        status: 405,
        headers: { "Content-Type": "application/json" },
      });
    }
    const body = await req.json().catch(() => ({}));
    const provider = body.provider || "BINANCE";
    const timeframe = body.timeframe || "D1";

    const result = await runPipeline(provider, timeframe);
    return new Response(JSON.stringify(result), {
      status: 200,
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    // Concurrency conflict
    if (msg.includes("already running")) {
      return new Response(JSON.stringify({ error: msg }), {
        status: 409,
        headers: { "Content-Type": "application/json" },
      });
    }
    return new Response(JSON.stringify({ error: msg }), {
      status: 500,
      headers: { "Content-Type": "application/json" },
    });
  }
});
