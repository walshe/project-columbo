package walshe.projectcolumbo.api.v1.summary;

/**
 * Output format for summary report endpoints.
 *
 * JSON      — structured JSON response (default); suitable for API consumers and AI assistants.
 * MARKDOWN  — plain-text Markdown; suitable for display in terminals, chat tools, or daily brief generation.
 * WATCHLIST — TradingView-importable watchlist text: ###Section headers with EXCHANGE:SYMBOL lines
 *             (e.g. BINANCE:BTCUSDT), so all flagged assets can be pulled into one chart. Served as text/plain.
 */
public enum SummaryFormat {
    JSON,
    MARKDOWN,
    WATCHLIST
}
