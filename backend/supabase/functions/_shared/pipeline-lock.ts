// Concurrency protection for pipeline runs.
// Port of Java IngestionOrchestrator concurrency check.

import { SupabaseClient } from "npm:@supabase/supabase-js@2";
import type { Provider, Timeframe } from "./types.ts";

/**
 * Checks if there's already a RUNNING ingestion run for the given provider/timeframe.
 * Throws if a run is already in progress.
 */
export async function assertNoRunningPipeline(
  supabase: SupabaseClient,
  provider: Provider,
  timeframe: Timeframe
): Promise<void> {
  const { data, error } = await supabase
    .from("ingestion_run")
    .select("id, started_at")
    .eq("provider", provider)
    .eq("timeframe", timeframe)
    .eq("status", "RUNNING")
    .limit(1);

  if (error) {
    throw new Error(`Failed to check running pipelines: ${error.message}`);
  }

  if (data && data.length > 0) {
    const run = data[0];
    throw new Error(
      `Pipeline already running: run ${run.id} started at ${run.started_at}`
    );
  }
}

/**
 * Creates a new RUNNING ingestion_run record and returns its ID.
 */
export async function createRunningRecord(
  supabase: SupabaseClient,
  provider: Provider,
  timeframe: Timeframe,
  assetCount: number
): Promise<number> {
  const { data, error } = await supabase
    .from("ingestion_run")
    .insert({
      provider,
      timeframe,
      started_at: new Date().toISOString(),
      status: "RUNNING",
      asset_count: assetCount,
      inserted_count: 0,
      updated_count: 0,
      skipped_count: 0,
      error_count: 0,
    })
    .select("id")
    .single();

  if (error) {
    throw new Error(`Failed to create ingestion run: ${error.message}`);
  }

  return data.id;
}

/**
 * Finalizes an ingestion_run record with metrics and status.
 * Status logic mirrors Java IngestionOrchestrator.deriveStatus():
 * - errorCount == 0 → SUCCESS
 * - errorCount > 0 but some inserts/updates/skips → PARTIAL
 * - otherwise → FAILED
 */
export async function finalizeRun(
  supabase: SupabaseClient,
  runId: number,
  metrics: {
    insertedCount: number;
    updatedCount: number;
    skippedCount: number;
    errorCount: number;
    errorSample: string | null;
  },
  startedAt: Date
): Promise<void> {
  const now = new Date();
  const durationMs = now.getTime() - startedAt.getTime();

  let status: string;
  if (metrics.errorCount === 0) {
    status = "SUCCESS";
  } else if (
    metrics.insertedCount > 0 ||
    metrics.updatedCount > 0 ||
    metrics.skippedCount > 0
  ) {
    status = "PARTIAL";
  } else {
    status = "FAILED";
  }

  const { error } = await supabase
    .from("ingestion_run")
    .update({
      finished_at: now.toISOString(),
      duration_ms: durationMs,
      status,
      inserted_count: metrics.insertedCount,
      updated_count: metrics.updatedCount,
      skipped_count: metrics.skippedCount,
      error_count: metrics.errorCount,
      error_sample: metrics.errorSample,
    })
    .eq("id", runId);

  if (error) {
    console.error(`Failed to finalize ingestion run ${runId}: ${error.message}`);
  }
}
