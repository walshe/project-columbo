// Port of Java CandleFilters.java
// Provides finalized candle boundary logic (UTC midnight today).

/**
 * Returns UTC midnight of the current day.
 * Candles with close_time >= this boundary are considered non-finalized.
 */
export function utcMidnightToday(now: Date = new Date()): Date {
  const d = new Date(
    Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate())
  );
  return d;
}

/**
 * Returns the finalized boundary as an ISO string for use in queries.
 */
export function getFinalizedBoundary(now: Date = new Date()): string {
  return utcMidnightToday(now).toISOString();
}

/**
 * Filters candles to only those with close_time strictly before UTC midnight today.
 * Returns them sorted oldest → newest by close_time.
 */
export function filterFinalized<T extends { closeTime: string }>(
  candles: T[],
  now: Date = new Date()
): T[] {
  const boundary = utcMidnightToday(now).getTime();
  return candles
    .filter((c) => new Date(c.closeTime).getTime() < boundary)
    .sort(
      (a, b) =>
        new Date(a.closeTime).getTime() - new Date(b.closeTime).getTime()
    );
}
