// Decimal arithmetic configuration matching Java BigDecimal(SCALE=10, HALF_UP)
// Uses decimal.js for deterministic computation parity with the Java backend.
// decimal.js has proper TypeScript definitions unlike decimal.js-light.

import { Decimal } from "npm:decimal.js@10.5.0";

// Match Java's BigDecimal settings:
// - SCALE = 10 decimal places
// - RoundingMode.HALF_UP = Decimal.ROUND_HALF_UP (4)
Decimal.set({
  precision: 20, // internal working precision (well above SCALE)
  rounding: Decimal.ROUND_HALF_UP,
});

export const SCALE = 10;

/** Division with SCALE=10 and HALF_UP rounding, matching Java BigDecimal.divide(n, 10, HALF_UP) */
export function div(a: Decimal, b: Decimal): Decimal {
  return a.div(b).toDecimalPlaces(SCALE, Decimal.ROUND_HALF_UP);
}

/** Multiply two Decimals (no scale truncation needed for multiplication) */
export function mul(a: Decimal, b: Decimal): Decimal {
  return a.mul(b);
}

/** Create a Decimal from a string or number */
export function d(value: string | number | Decimal): Decimal {
  return new Decimal(value);
}

export { Decimal };
export default Decimal;
