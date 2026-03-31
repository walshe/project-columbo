// Port of Java SignalStateCalculator.java (SuperTrend portion only; RSI excluded)
// Pure, stateless calculator for signal state detection.

import type {
  SuperTrendDirection,
  TrendState,
  SignalEvent,
  SignalStateResult,
} from "./types.ts";

interface IndicatorInput {
  closeTime: string;
  direction: SuperTrendDirection;
}

/**
 * Maps SuperTrend direction to trend state.
 * Port of Java SignalStateCalculator.mapDirection().
 */
function mapDirection(direction: SuperTrendDirection): TrendState {
  return direction === "UP" ? "BULLISH" : "BEARISH";
}

/**
 * Calculates trend states and detects reversals from a list of SuperTrend indicators.
 * Port of Java SignalStateCalculator.calculate().
 *
 * @param indicators Ordered list of SuperTrend indicator results (oldest → newest)
 * @param previousDirection The last known direction from a prior run (null if fresh start)
 * @returns List of SignalStateResult with trendState and event for each indicator
 */
export function calculate(
  indicators: IndicatorInput[],
  previousDirection: SuperTrendDirection | null = null
): SignalStateResult[] {
  const results: SignalStateResult[] = [];
  let lastDirection: SuperTrendDirection | null = previousDirection;

  for (const indicator of indicators) {
    const currentDirection = indicator.direction;
    const trendState = mapDirection(currentDirection);
    let event: SignalEvent;

    if (lastDirection === null) {
      // First data point — no prior comparison possible
      event = "NONE";
    } else if (lastDirection === "DOWN" && currentDirection === "UP") {
      // Bearish → Bullish transition
      event = "BULLISH_REVERSAL";
    } else if (lastDirection === "UP" && currentDirection === "DOWN") {
      // Bullish → Bearish transition
      event = "BEARISH_REVERSAL";
    } else {
      // No change in direction
      event = "NONE";
    }

    results.push({
      closeTime: indicator.closeTime,
      trendState,
      event,
    });

    lastDirection = currentDirection;
  }

  return results;
}
