// Trigger Pipeline Edge Function
// HTTP entry point that triggers the full daily pipeline.
// Port of Java IngestionController POST /api/v1/internal/ingestion/run
//
// Delegates to run-pipeline via HTTP (not via import) so the orchestrator
// runs in its own isolate. This is part of the HTTP-delegation architecture
// used to work around the Edge Runtime CPU time limit — see run-pipeline/index.ts
// for full documentation on the architecture and transactional trade-offs.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";

Deno.serve(async (req) => {
  // Only accept POST
  if (req.method !== "POST") {
    return new Response(JSON.stringify({ error: "Method not allowed" }), {
      status: 405,
      headers: { "Content-Type": "application/json" },
    });
  }

  try {
    const body = await req.json().catch(() => ({}));
    const provider = body.provider || "BINANCE";
    const timeframe = body.timeframe || "D1";

    console.log(`Trigger pipeline: ${provider} ${timeframe}`);

    // Call run-pipeline as a separate Edge Function via HTTP
    const supabaseUrl = Deno.env.get("SUPABASE_URL")!;
    const serviceRoleKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;

    const response = await fetch(
      `${supabaseUrl}/functions/v1/run-pipeline`,
      {
        method: "POST",
        headers: {
          "Authorization": `Bearer ${serviceRoleKey}`,
          "Content-Type": "application/json",
        },
        body: JSON.stringify({ provider, timeframe }),
      }
    );

    const result = await response.json();

    return new Response(JSON.stringify(result), {
      status: response.status,
      headers: { "Content-Type": "application/json" },
    });
  } catch (e) {
    const msg = e instanceof Error ? e.message : String(e);
    console.error(`Trigger pipeline error: ${msg}`);
    return new Response(
      JSON.stringify({ error: msg }),
      { status: 500, headers: { "Content-Type": "application/json" } }
    );
  }
});
