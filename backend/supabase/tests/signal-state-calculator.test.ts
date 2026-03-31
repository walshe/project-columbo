// Unit tests for Signal State Calculator
// Validates all transition cases for SuperTrend direction → signal events.

import { assertEquals } from "https://deno.land/std@0.220.0/assert/mod.ts";
import { calculate } from "../functions/_shared/signal-state-calculator.ts";

Deno.test("empty input returns empty results", () => {
  const results = calculate([]);
  assertEquals(results.length, 0);
});

Deno.test("single UP indicator with no previous → BULLISH + NONE", () => {
  const results = calculate([
    { closeTime: "2025-01-01T00:00:00Z", direction: "UP" },
  ]);
  assertEquals(results.length, 1);
  assertEquals(results[0].trendState, "BULLISH");
  assertEquals(results[0].event, "NONE");
});

Deno.test("single DOWN indicator with no previous → BEARISH + NONE", () => {
  const results = calculate([
    { closeTime: "2025-01-01T00:00:00Z", direction: "DOWN" },
  ]);
  assertEquals(results.length, 1);
  assertEquals(results[0].trendState, "BEARISH");
  assertEquals(results[0].event, "NONE");
});

Deno.test("DOWN → UP transition = BULLISH_REVERSAL", () => {
  const results = calculate([
    { closeTime: "2025-01-01T00:00:00Z", direction: "DOWN" },
    { closeTime: "2025-01-02T00:00:00Z", direction: "UP" },
  ]);
  assertEquals(results[0].trendState, "BEARISH");
  assertEquals(results[0].event, "NONE");
  assertEquals(results[1].trendState, "BULLISH");
  assertEquals(results[1].event, "BULLISH_REVERSAL");
});

Deno.test("UP → DOWN transition = BEARISH_REVERSAL", () => {
  const results = calculate([
    { closeTime: "2025-01-01T00:00:00Z", direction: "UP" },
    { closeTime: "2025-01-02T00:00:00Z", direction: "DOWN" },
  ]);
  assertEquals(results[0].trendState, "BULLISH");
  assertEquals(results[0].event, "NONE");
  assertEquals(results[1].trendState, "BEARISH");
  assertEquals(results[1].event, "BEARISH_REVERSAL");
});

Deno.test("same direction = NONE event", () => {
  const results = calculate([
    { closeTime: "2025-01-01T00:00:00Z", direction: "UP" },
    { closeTime: "2025-01-02T00:00:00Z", direction: "UP" },
    { closeTime: "2025-01-03T00:00:00Z", direction: "UP" },
  ]);
  for (const r of results) {
    assertEquals(r.trendState, "BULLISH");
    assertEquals(r.event, "NONE");
  }
});

Deno.test("previousDirection DOWN, first indicator UP = BULLISH_REVERSAL", () => {
  const results = calculate(
    [{ closeTime: "2025-01-01T00:00:00Z", direction: "UP" }],
    "DOWN"
  );
  assertEquals(results[0].trendState, "BULLISH");
  assertEquals(results[0].event, "BULLISH_REVERSAL");
});

Deno.test("previousDirection UP, first indicator DOWN = BEARISH_REVERSAL", () => {
  const results = calculate(
    [{ closeTime: "2025-01-01T00:00:00Z", direction: "DOWN" }],
    "UP"
  );
  assertEquals(results[0].trendState, "BEARISH");
  assertEquals(results[0].event, "BEARISH_REVERSAL");
});

Deno.test("previousDirection UP, first indicator UP = NONE", () => {
  const results = calculate(
    [{ closeTime: "2025-01-01T00:00:00Z", direction: "UP" }],
    "UP"
  );
  assertEquals(results[0].trendState, "BULLISH");
  assertEquals(results[0].event, "NONE");
});

Deno.test("multiple transitions tracked correctly", () => {
  const results = calculate([
    { closeTime: "2025-01-01T00:00:00Z", direction: "UP" },
    { closeTime: "2025-01-02T00:00:00Z", direction: "UP" },
    { closeTime: "2025-01-03T00:00:00Z", direction: "DOWN" },
    { closeTime: "2025-01-04T00:00:00Z", direction: "DOWN" },
    { closeTime: "2025-01-05T00:00:00Z", direction: "UP" },
  ]);

  assertEquals(results[0].event, "NONE"); // first, no prior
  assertEquals(results[1].event, "NONE"); // UP → UP
  assertEquals(results[2].event, "BEARISH_REVERSAL"); // UP → DOWN
  assertEquals(results[3].event, "NONE"); // DOWN → DOWN
  assertEquals(results[4].event, "BULLISH_REVERSAL"); // DOWN → UP
});

Deno.test("closeTime preserved in output", () => {
  const input = [
    { closeTime: "2025-06-15T00:00:00Z", direction: "UP" as const },
    { closeTime: "2025-06-16T00:00:00Z", direction: "DOWN" as const },
  ];
  const results = calculate(input);
  assertEquals(results[0].closeTime, "2025-06-15T00:00:00Z");
  assertEquals(results[1].closeTime, "2025-06-16T00:00:00Z");
});
