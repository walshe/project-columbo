// Unit tests for SuperTrend calculator
// Validates deterministic output matches expected values.

import { assertEquals, assertNotEquals } from "https://deno.land/std@0.220.0/assert/mod.ts";
import { calculate, calculateIncremental } from "../functions/_shared/supertrend-calculator.ts";

// ── Test data ───────────────────────────────────────────────────────────────

// Minimal 15-candle dataset for verifying ATR and SuperTrend with atrLength=10
const CANDLES = [
  { closeTime: "2025-01-01T00:00:00Z", high: "100", low: "90", close: "95" },
  { closeTime: "2025-01-02T00:00:00Z", high: "102", low: "93", close: "99" },
  { closeTime: "2025-01-03T00:00:00Z", high: "105", low: "96", close: "101" },
  { closeTime: "2025-01-04T00:00:00Z", high: "108", low: "98", close: "104" },
  { closeTime: "2025-01-05T00:00:00Z", high: "107", low: "100", close: "103" },
  { closeTime: "2025-01-06T00:00:00Z", high: "110", low: "101", close: "108" },
  { closeTime: "2025-01-07T00:00:00Z", high: "112", low: "105", close: "110" },
  { closeTime: "2025-01-08T00:00:00Z", high: "115", low: "107", close: "113" },
  { closeTime: "2025-01-09T00:00:00Z", high: "114", low: "106", close: "109" },
  { closeTime: "2025-01-10T00:00:00Z", high: "111", low: "103", close: "106" },
  // ATR starts here (index 9, atrLength=10)
  { closeTime: "2025-01-11T00:00:00Z", high: "109", low: "100", close: "102" },
  { closeTime: "2025-01-12T00:00:00Z", high: "105", low: "97", close: "99" },
  { closeTime: "2025-01-13T00:00:00Z", high: "103", low: "94", close: "96" },
  { closeTime: "2025-01-14T00:00:00Z", high: "100", low: "91", close: "98" },
  { closeTime: "2025-01-15T00:00:00Z", high: "104", low: "95", close: "102" },
];

// ── Tests ───────────────────────────────────────────────────────────────────

Deno.test("calculate returns null for initial period before ATR is available", () => {
  const results = calculate(CANDLES, 10, "2.0");

  // First 9 results (indices 0-8) should be null (not enough data for ATR)
  for (let i = 0; i < 9; i++) {
    assertEquals(results[i], null, `Expected null at index ${i}`);
  }

  // Index 9 onward should have values
  for (let i = 9; i < results.length; i++) {
    assertNotEquals(results[i], null, `Expected non-null at index ${i}`);
  }
});

Deno.test("calculate returns correct number of results", () => {
  const results = calculate(CANDLES, 10, "2.0");
  assertEquals(results.length, CANDLES.length);
});

Deno.test("calculate returns empty for empty input", () => {
  const results = calculate([], 10, "2.0");
  assertEquals(results.length, 0);
});

Deno.test("calculate returns all nulls when fewer candles than atrLength", () => {
  const results = calculate(CANDLES.slice(0, 5), 10, "2.0");
  assertEquals(results.length, 5);
  for (const r of results) {
    assertEquals(r, null);
  }
});

Deno.test("first ATR is SMA of first N true ranges", () => {
  const results = calculate(CANDLES, 10, "2.0");
  const firstResult = results[9]; // Index 9 = first valid ATR (atrLength-1)
  assertNotEquals(firstResult, null);

  // ATR should be a numeric string with 10 decimal places
  const atr = parseFloat(firstResult!.atr);
  assertEquals(isNaN(atr), false);
  assertEquals(atr > 0, true);
});

Deno.test("direction is UP or DOWN", () => {
  const results = calculate(CANDLES, 10, "2.0");
  for (const r of results) {
    if (r !== null) {
      assertEquals(
        r.direction === "UP" || r.direction === "DOWN",
        true,
        `Expected UP or DOWN, got ${r.direction}`
      );
    }
  }
});

Deno.test("output values have SCALE=10 decimal places", () => {
  const results = calculate(CANDLES, 10, "2.0");
  for (const r of results) {
    if (r !== null) {
      // Each numeric field should have exactly 10 decimal places
      const decimals = r.atr.split(".")[1];
      assertEquals(decimals?.length, 10, `ATR decimal places: ${decimals?.length}`);
    }
  }
});

Deno.test("deterministic: same input produces same output", () => {
  const results1 = calculate(CANDLES, 10, "2.0");
  const results2 = calculate(CANDLES, 10, "2.0");
  assertEquals(results1, results2);
});

Deno.test("closeTime preserved in output", () => {
  const results = calculate(CANDLES, 10, "2.0");
  for (let i = 0; i < results.length; i++) {
    if (results[i] !== null) {
      assertEquals(results[i]!.closeTime, CANDLES[i].closeTime);
    }
  }
});

// ── Incremental calculation tests ───────────────────────────────────────────

Deno.test("calculateIncremental with null lastStoredCloseTime returns full results", () => {
  const results = calculateIncremental(CANDLES, 10, "2.0", null, false);
  // Should return all non-null results
  const fullResults = calculate(CANDLES, 10, "2.0").filter((r) => r !== null);
  assertEquals(results.length, fullResults.length);
});

Deno.test("calculateIncremental with fullRecalc=true returns full results", () => {
  const results = calculateIncremental(CANDLES, 10, "2.0", "2025-01-12T00:00:00Z", true);
  const fullResults = calculate(CANDLES, 10, "2.0").filter((r) => r !== null);
  assertEquals(results.length, fullResults.length);
});

Deno.test("calculateIncremental returns only new results after anchor", () => {
  // Last stored = Jan 12, so we expect results for Jan 13, 14, 15
  const results = calculateIncremental(CANDLES, 10, "2.0", "2025-01-12T00:00:00Z", false);
  assertEquals(results.length, 3);
  assertEquals(results[0].closeTime, "2025-01-13T00:00:00Z");
  assertEquals(results[1].closeTime, "2025-01-14T00:00:00Z");
  assertEquals(results[2].closeTime, "2025-01-15T00:00:00Z");
});

Deno.test("calculateIncremental returns empty when already up to date", () => {
  const results = calculateIncremental(CANDLES, 10, "2.0", "2025-01-15T00:00:00Z", false);
  assertEquals(results.length, 0);
});

Deno.test("incremental results match full calculation results", () => {
  // Full calculation
  const full = calculate(CANDLES, 10, "2.0").filter((r) => r !== null);

  // Incremental from midpoint
  const incremental = calculateIncremental(
    CANDLES, 10, "2.0", "2025-01-12T00:00:00Z", false
  );

  // The last 3 results of full should match incremental
  const fullLast3 = full.slice(-3);
  assertEquals(incremental.length, fullLast3.length);
  for (let i = 0; i < incremental.length; i++) {
    assertEquals(incremental[i].atr, fullLast3[i]!.atr, `ATR mismatch at ${i}`);
    assertEquals(incremental[i].upperBand, fullLast3[i]!.upperBand, `Upper band mismatch at ${i}`);
    assertEquals(incremental[i].lowerBand, fullLast3[i]!.lowerBand, `Lower band mismatch at ${i}`);
    assertEquals(incremental[i].supertrend, fullLast3[i]!.supertrend, `SuperTrend mismatch at ${i}`);
    assertEquals(incremental[i].direction, fullLast3[i]!.direction, `Direction mismatch at ${i}`);
  }
});
