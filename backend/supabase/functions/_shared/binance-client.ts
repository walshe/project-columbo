// Port of Java BinanceMarketDataProvider.java
// Fetches daily OHLCV candles from the Binance REST API.

import type { CandleDto } from "./types.ts";

const DEFAULT_BASE_URL = "https://api.binance.com";
const KLINES_ENDPOINT = "/api/v3/klines";
const INTER_REQUEST_DELAY_MS = 200; // Match Java Thread.sleep(200)

/**
 * Normalizes a symbol for the Binance API.
 * Port of Java BinanceMarketDataProvider.normalizeSymbol().
 */
export function normalizeSymbol(symbol: string): string {
  let normalized = symbol.replace(/[\/\-]/g, "").toUpperCase();
  if (!normalized.endsWith("USDT")) {
    normalized += "USDT";
  }
  return normalized;
}

/**
 * Fetches daily candles from Binance for a single symbol.
 * Port of Java BinanceMarketDataProvider.fetchDailyCandles().
 *
 * @param symbol Trading pair symbol (e.g. "BTCUSDT")
 * @param startTime Start time in epoch milliseconds (optional)
 * @param endTime End time in epoch milliseconds (optional)
 * @returns List of CandleDto
 */
export async function fetchDailyCandles(
  symbol: string,
  startTime?: number,
  endTime?: number
): Promise<CandleDto[]> {
  const baseUrl = Deno.env.get("BINANCE_BASE_URL") || DEFAULT_BASE_URL;
  const normalizedSymbol = normalizeSymbol(symbol);

  const params = new URLSearchParams({
    symbol: normalizedSymbol,
    interval: "1d",
  });
  if (startTime !== undefined) params.set("startTime", String(startTime));
  if (endTime !== undefined) params.set("endTime", String(endTime));

  const url = `${baseUrl}${KLINES_ENDPOINT}?${params.toString()}`;

  const response = await fetch(url);

  if (!response.ok) {
    const body = await response.text();
    // Binance returns -1121 for invalid symbols
    if (body.includes("-1121")) {
      console.warn(`Invalid symbol on Binance: ${normalizedSymbol}`);
      return [];
    }
    throw new Error(
      `Binance API error for ${normalizedSymbol}: ${response.status} ${body}`
    );
  }

  const data: unknown[][] = await response.json();

  if (!data || !Array.isArray(data)) {
    console.warn(`Empty response from Binance for ${normalizedSymbol}`);
    return [];
  }

  return data
    .filter((row) => row.length >= 8)
    .map((row) => mapToCandleDto(row))
    .filter((c): c is CandleDto => c !== null);
}

/**
 * Fetches candles for multiple assets with a delay between requests.
 * Mirrors Java's per-asset delay pattern.
 */
export async function fetchDailyCandlesWithDelay(
  symbol: string,
  startTime?: number,
  endTime?: number
): Promise<CandleDto[]> {
  const result = await fetchDailyCandles(symbol, startTime, endTime);
  // Delay between requests to respect rate limits
  await sleep(INTER_REQUEST_DELAY_MS);
  return result;
}

// ── Internal helpers ────────────────────────────────────────────────────────

/**
 * Maps a Binance kline response row to a CandleDto.
 * Port of Java BinanceMarketDataProvider.mapToCandleDto().
 *
 * Binance kline format:
 * [0] openTime(ms), [1] open, [2] high, [3] low, [4] close,
 * [5] volume, [6] closeTime(ms), [7] quoteAssetVolume,
 * [8] numberOfTrades, [9] takerBuyBaseVolume, [10] takerBuyQuoteVolume, [11] ignore
 *
 * NOTE: Java uses index [7] (quoteAssetVolume) for volume, not [5].
 */
function mapToCandleDto(row: unknown[]): CandleDto | null {
  try {
    const openTimeMs = Number(row[0]);
    const open = String(row[1]);
    const high = String(row[2]);
    const low = String(row[3]);
    const close = String(row[4]);
    const volume = String(row[7]); // quoteAssetVolume, matching Java
    const closeTimeMs = Number(row[6]);

    return {
      open,
      high,
      low,
      close,
      volume,
      openTime: new Date(openTimeMs).toISOString(),
      closeTime: new Date(closeTimeMs).toISOString(),
    };
  } catch (e) {
    console.error(`Error parsing Binance candle data: ${JSON.stringify(row)}`, e);
    return null;
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms));
}
