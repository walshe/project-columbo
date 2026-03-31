// Summary Report Edge Function
// Port of Java SummaryService + SummaryReportFormatter (minus RSI scan sections)
// Returns market summary as JSON, Markdown, or HTML.

import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { getSupabaseClient } from "../_shared/supabase-client.ts";

interface SignalRow {
  symbol: string;
  trend_state: string;
  event: string;
  close_time: string;
  last_flip_time: string | null;
  days_since_flip: number | null;
  avg_volume_7d: number;
  tradingview_url: string;
}

interface PulseRow {
  snapshot_close_time: string;
  bullish_count: number;
  bearish_count: number;
  missing_count: number;
  total_assets: number;
  bullish_ratio: string;
}

interface SummaryReport {
  pulse: PulseRow | null;
  bullishSignals: SignalRow[];
  bearishSignals: SignalRow[];
}

async function getSummary(timeframe: string): Promise<SummaryReport> {
  const supabase = getSupabaseClient();

  // Fetch market pulse
  const { data: pulseData } = await supabase.rpc("get_market_pulse_latest", {
    p_timeframe: timeframe,
    p_indicator_type: "SUPERTREND",
  });
  const pulse: PulseRow | null =
    pulseData && pulseData.length > 0 ? pulseData[0] : null;

  // Fetch bullish signals (sorted by last flip desc)
  const { data: bullishData } = await supabase.rpc("get_signals", {
    p_timeframe: timeframe,
    p_indicator_type: "SUPERTREND",
    p_state_filter: "BULLISH",
    p_sort: "LAST_FLIP_DESC",
  });

  // Fetch bearish signals (sorted by last flip desc)
  const { data: bearishData } = await supabase.rpc("get_signals", {
    p_timeframe: timeframe,
    p_indicator_type: "SUPERTREND",
    p_state_filter: "BEARISH",
    p_sort: "LAST_FLIP_DESC",
  });

  return {
    pulse,
    bullishSignals: bullishData || [],
    bearishSignals: bearishData || [],
  };
}

// ── Formatters ──────────────────────────────────────────────────────────────

function formatVolume(volume: number | null): string {
  if (!volume || volume === 0) return "N/A";
  if (volume >= 1_000_000) return `${(volume / 1_000_000).toFixed(1)}M`;
  if (volume >= 1_000) return `${(volume / 1_000).toFixed(1)}K`;
  return `${Math.round(volume)}`;
}

function formatMarkdown(report: SummaryReport): string {
  let md = "# Market Summary Report\n\n";

  if (report.pulse) {
    const p = report.pulse;
    md += "## Market Pulse\n";
    md += `- **Bullish:** ${p.bullish_count}\n`;
    md += `- **Bearish:** ${p.bearish_count}\n`;
    md += `- **Bullish Ratio:** ${(parseFloat(p.bullish_ratio) * 100).toFixed(2)}%\n\n`;
  }

  md += "## Recent Bullish Flips\n";
  md += formatSignalsMarkdown(report.bullishSignals);

  md += "## Recent Bearish Flips\n";
  md += formatSignalsMarkdown(report.bearishSignals);

  return md;
}

function formatSignalsMarkdown(signals: SignalRow[]): string {
  if (signals.length === 0) return "None found.\n\n";
  return (
    signals
      .map(
        (s) =>
          `- [${s.symbol}](${s.tradingview_url}): Flipped ${s.days_since_flip ?? "?"} days ago (Vol: ${formatVolume(s.avg_volume_7d)})`
      )
      .join("\n") + "\n\n"
  );
}

function formatHtml(report: SummaryReport): string {
  let html = "<h1>Market Summary Report</h1>";

  if (report.pulse) {
    const p = report.pulse;
    html += "<h2>Market Pulse</h2><ul>";
    html += `<li><strong>Bullish:</strong> ${p.bullish_count}</li>`;
    html += `<li><strong>Bearish:</strong> ${p.bearish_count}</li>`;
    html += `<li><strong>Bullish Ratio:</strong> ${(parseFloat(p.bullish_ratio) * 100).toFixed(2)}%</li>`;
    html += "</ul>";
  }

  html += "<h2>Recent Bullish Flips</h2>";
  html += formatSignalsHtml(report.bullishSignals);

  html += "<h2>Recent Bearish Flips</h2>";
  html += formatSignalsHtml(report.bearishSignals);

  return html;
}

function formatSignalsHtml(signals: SignalRow[]): string {
  if (signals.length === 0) return "<p>None found.</p>";
  return (
    "<ul>" +
    signals
      .map(
        (s) =>
          `<li><a href="${s.tradingview_url}">${s.symbol}</a>: Flipped ${s.days_since_flip ?? "?"} days ago (Vol: ${formatVolume(s.avg_volume_7d)})</li>`
      )
      .join("") +
    "</ul>"
  );
}

// ── HTTP handler ────────────────────────────────────────────────────────────

Deno.serve(async (req) => {
  try {
    const url = new URL(req.url);
    const timeframe = url.searchParams.get("timeframe") || "D1";
    const format = url.searchParams.get("format") || "json";

    const report = await getSummary(timeframe);

    if (format === "markdown") {
      return new Response(formatMarkdown(report), {
        status: 200,
        headers: { "Content-Type": "text/markdown; charset=utf-8" },
      });
    }

    if (format === "html") {
      return new Response(formatHtml(report), {
        status: 200,
        headers: { "Content-Type": "text/html; charset=utf-8" },
      });
    }

    // Default: JSON
    return new Response(JSON.stringify(report, null, 2), {
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
