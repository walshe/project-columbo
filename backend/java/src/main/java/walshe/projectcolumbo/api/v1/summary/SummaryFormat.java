package walshe.projectcolumbo.api.v1.summary;

/**
 * Output format for summary report endpoints.
 *
 * JSON     — structured JSON response (default); suitable for API consumers and AI assistants.
 * MARKDOWN — plain-text Markdown; suitable for display in terminals, chat tools, or daily brief generation.
 */
public enum SummaryFormat {
    JSON,
    MARKDOWN
}
