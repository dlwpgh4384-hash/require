/** Decimal text HALF_UP, sign-symmetric; quantities keep one fractional digit. */
export function formatDecimal(
  value: number | string | null | undefined,
): string {
  if (value == null || (typeof value === "number" && !Number.isFinite(value)))
    return "–";
  const match = /^([+-]?)(\d+)(?:\.(\d*))?(?:e([+-]?\d+))?$/i.exec(
    String(value),
  );
  if (!match) return "–";
  const [, sign, whole, fraction = "", exponent = "0"] = match;
  const shift = Number(exponent) - fraction.length + 1;
  let magnitude = BigInt(whole + fraction);
  if (shift >= 0) magnitude *= 10n ** BigInt(shift);
  else {
    const divisor = 10n ** BigInt(-shift);
    magnitude =
      magnitude / divisor + ((magnitude % divisor) * 2n >= divisor ? 1n : 0n);
  }
  const digits = magnitude.toString().padStart(2, "0");
  return `${sign === "-" && magnitude !== 0n ? "−" : ""}${digits.slice(0, -1)}.${digits.slice(-1)}`;
}
