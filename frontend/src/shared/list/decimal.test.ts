import { expect, it } from "vitest";
import { formatDecimal } from "./decimal";

it.each([
  [1.25, "1.3"],
  [-1.25, "−1.3"],
  [1.005, "1.0"],
  [-0.05, "−0.1"],
  [-0.049, "0.0"],
  [-0, "0.0"],
  [0, "0.0"],
  [1234.55, "1234.6"],
  ["9.95", "10.0"],
  ["-9.95", "−10.0"],
  ["5e-2", "0.1"],
  ["-5e-2", "−0.1"],
  [1e-7, "0.0"],
  [null, "–"],
  [NaN, "–"],
  [Infinity, "–"],
  ["invalid", "–"],
] as const)("rounds %s as decimal HALF_UP to %s", (value, expected) => {
  expect(formatDecimal(value)).toBe(expected);
});
