// Port of Java SuperTrendCalculator.java
// Pure, stateless calculator for SuperTrend indicator.
// Uses decimal.js-light for BigDecimal parity with Java (SCALE=10, HALF_UP).

import { Decimal, d, div, mul, SCALE } from "./decimal.ts";
import type { SuperTrendDirection, SuperTrendResult } from "./types.ts";

interface CandleInput {
  closeTime: string;
  high: string;
  low: string;
  close: string;
}

/**
 * Calculates SuperTrend for a list of candles.
 * Direct port of Java SuperTrendCalculator.calculate().
 *
 * @param candles Ordered list (oldest → newest)
 * @param atrLength ATR smoothing period (e.g. 10)
 * @param multiplier ATR multiplier (e.g. 2.0)
 * @returns List of results; null entries for initial period before ATR is available
 */
export function calculate(
  candles: CandleInput[],
  atrLength: number,
  multiplier: number | string
): (SuperTrendResult | null)[] {
  const results: (SuperTrendResult | null)[] = [];
  if (candles.length === 0) return results;

  const mult = d(multiplier);
  const tr = calculateTrueRanges(candles);
  const atr = calculateATR(tr, atrLength);

  let prevFinalUpper: Decimal | null = null;
  let prevFinalLower: Decimal | null = null;
  let prevSuperTrend: Decimal | null = null;
  let prevClose: Decimal | null = null;

  for (let i = 0; i < candles.length; i++) {
    const candle = candles[i];
    const currentAtr = atr[i];

    if (currentAtr === null) {
      results.push(null);
      prevClose = d(candle.close);
      continue;
    }

    // 3. Basic Bands
    const high = d(candle.high);
    const low = d(candle.low);
    const close = d(candle.close);
    const middle = div(high.plus(low), d(2));

    const basicUpper = middle.plus(mul(mult, currentAtr));
    const basicLower = middle.minus(mul(mult, currentAtr));

    // 4. Final Bands (sticky logic)
    let finalUpper: Decimal;
    if (
      prevFinalUpper === null ||
      basicUpper.lt(prevFinalUpper) ||
      prevClose!.gt(prevFinalUpper)
    ) {
      finalUpper = basicUpper;
    } else {
      finalUpper = prevFinalUpper;
    }

    let finalLower: Decimal;
    if (
      prevFinalLower === null ||
      basicLower.gt(prevFinalLower) ||
      prevClose!.lt(prevFinalLower)
    ) {
      finalLower = basicLower;
    } else {
      finalLower = prevFinalLower;
    }

    // 5. SuperTrend
    let superTrend: Decimal;
    if (prevSuperTrend === null) {
      // Initial direction: assume DOWN if close <= finalUpper, else UP
      superTrend = close.lte(finalUpper) ? finalUpper : finalLower;
    } else if (prevSuperTrend.eq(prevFinalUpper!)) {
      superTrend = close.lte(finalUpper) ? finalUpper : finalLower;
    } else {
      // prevSuperTrend == prevFinalLower
      superTrend = close.gte(finalLower) ? finalLower : finalUpper;
    }

    // 6. Direction
    const direction: SuperTrendDirection = superTrend.eq(finalLower)
      ? "UP"
      : "DOWN";

    results.push({
      closeTime: candle.closeTime,
      atr: currentAtr.toFixed(SCALE),
      upperBand: finalUpper.toFixed(SCALE),
      lowerBand: finalLower.toFixed(SCALE),
      supertrend: superTrend.toFixed(SCALE),
      direction,
    });

    prevFinalUpper = finalUpper;
    prevFinalLower = finalLower;
    prevSuperTrend = superTrend;
    prevClose = close;
  }

  return results;
}

/**
 * Incremental calculation. Recomputes from a warm-up window around the last stored
 * close time to preserve ATR continuity, and returns only new results after the anchor.
 * Direct port of Java SuperTrendCalculator.calculateIncremental().
 */
export function calculateIncremental(
  candles: CandleInput[],
  atrLength: number,
  multiplier: number | string,
  lastStoredCloseTime: string | null,
  fullRecalc: boolean
): SuperTrendResult[] {
  if (fullRecalc || lastStoredCloseTime === null) {
    return calculate(candles, atrLength, multiplier).filter(
      (r): r is SuperTrendResult => r !== null
    );
  }
  if (candles.length === 0) return [];

  // Find anchor index: exact match, else first greater; if none → up to date
  const lastStoredMs = new Date(lastStoredCloseTime).getTime();
  let anchorIndex = -1;
  for (let i = 0; i < candles.length; i++) {
    const ct = new Date(candles[i].closeTime).getTime();
    if (ct === lastStoredMs) {
      anchorIndex = i;
      break;
    }
    if (ct > lastStoredMs) {
      anchorIndex = i;
      break;
    }
  }
  if (anchorIndex === -1) return []; // already up to date

  // Start from a warm-up window before the anchor point.
  // atrLength * 10 ensures ATR (Wilder's smoothing) has enough history to stabilize.
  const start = Math.max(0, anchorIndex - atrLength * 10);
  const window = candles.slice(start);
  const windowResults = calculate(window, atrLength, multiplier);

  // Return only results with closeTime strictly after lastStoredCloseTime
  const out: SuperTrendResult[] = [];
  for (const r of windowResults) {
    if (r === null) continue;
    if (new Date(r.closeTime).getTime() > lastStoredMs) {
      out.push(r);
    }
  }
  return out;
}

// ── Internal helpers ────────────────────────────────────────────────────────

function calculateTrueRanges(candles: CandleInput[]): (Decimal | null)[] {
  const tr: (Decimal | null)[] = new Array(candles.length);
  let prevClose: Decimal | null = null;

  for (let i = 0; i < candles.length; i++) {
    const high = d(candles[i].high);
    const low = d(candles[i].low);
    const highMinusLow = high.minus(low);

    if (prevClose === null) {
      tr[i] = highMinusLow;
    } else {
      const highMinusPrevClose = high.minus(prevClose).abs();
      const lowMinusPrevClose = low.minus(prevClose).abs();
      tr[i] = Decimal.max(highMinusLow, highMinusPrevClose, lowMinusPrevClose);
    }
    prevClose = d(candles[i].close);
  }
  return tr;
}

function calculateATR(
  tr: (Decimal | null)[],
  n: number
): (Decimal | null)[] {
  const atr: (Decimal | null)[] = new Array(tr.length).fill(null);
  if (tr.length < n) return atr;

  // First ATR = simple average of first n True Ranges
  let sum = d(0);
  for (let i = 0; i < n; i++) {
    sum = sum.plus(tr[i]!);
  }
  atr[n - 1] = div(sum, d(n));

  // Subsequent ATR: Wilder smoothing = (prevATR * (n-1) + currentTR) / n
  const nMinusOne = d(n - 1);
  const nDivisor = d(n);

  for (let i = n; i < tr.length; i++) {
    const prevAtr = atr[i - 1]!;
    atr[i] = div(prevAtr.mul(nMinusOne).plus(tr[i]!), nDivisor);
  }
  return atr;
}
