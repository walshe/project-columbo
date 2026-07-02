package walshe.projectcolumbo.api.v1.summary;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Output format for summary report endpoints.
 *
 * JSON      — structured JSON response (default); suitable for API consumers and AI assistants.
 * MARKDOWN  — plain-text Markdown; suitable for display in terminals, chat tools, or daily brief generation.
 * WATCHLIST — TradingView-importable watchlist text: ###Section headers with EXCHANGE:SYMBOL lines
 *             (e.g. BINANCE:BTCUSDT), so all flagged assets can be pulled into one chart. Served as text/plain.
 */
@Schema(description = "Output format for the report. "
        + "JSON = structured JSON body (default). "
        + "MARKDOWN = human-readable Markdown brief (text/markdown). "
        + "WATCHLIST = TradingView-importable watchlist text (text/plain): '###Section' headers matching the "
        + "report's sections, with one 'EXCHANGE:SYMBOL' line per asset (e.g. BINANCE:BTCUSDT), so every flagged "
        + "asset can be imported/pasted into a single TradingView chart instead of opening one link at a time.")
public enum SummaryFormat {
    JSON,
    MARKDOWN,
    WATCHLIST
}
