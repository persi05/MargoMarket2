const GOLD_LIMIT = 9_000_000_000_000_000n;
export function parseDecimalUnits(input: string, decimals: number, limit: bigint): number | null {
  const value = input.trim().replace(',', '.');
  if (!new RegExp('^\\d+(?:\\.\\d{1,' + decimals + '})?$').test(value)) return null;
  const [whole, fraction = ''] = value.split('.');
  if (whole.length > 16) return null;
  const units = BigInt(whole) * 10n ** BigInt(decimals) + BigInt(fraction.padEnd(decimals, '0'));
  return units > 0n && units <= limit ? Number(units) : null;
}
export const parseGoldMillions = (input: string) => parseDecimalUnits(input, 6, GOLD_LIMIT);
export const parsePln = (input: string) => parseDecimalUnits(input, 2, 100_000_000n);
function decimal(value: bigint, divisor: bigint, places: number): string {
  const remainder = (value % divisor).toString().padStart(places, '0').replace(/0+$/, '');
  return (value / divisor).toLocaleString('pl-PL') + (remainder ? ',' + remainder : '');
}
export function formatGold(value: number): string {
  if (!Number.isSafeInteger(value) || value < 1) return '—';
  const n = BigInt(value);
  return n >= 1_000_000_000n
    ? decimal(n, 1_000_000_000n, 9) + 'g'
    : n >= 1_000_000n
      ? decimal(n, 1_000_000n, 6) + 'm'
      : n.toLocaleString('pl-PL') + ' złota';
}
export function goldMillionsInput(value: number): string {
  const n = BigInt(value);
  return (
    (n / 1_000_000n).toString() +
    (n % 1_000_000n ? ',' + (n % 1_000_000n).toString().padStart(6, '0').replace(/0+$/, '') : '')
  );
}
export function formatPln(value: number | null): string {
  return value === null
    ? 'Zaloguj się, aby zobaczyć cenę'
    : decimal(BigInt(value), 100n, 2) + ' PLN';
}

export type GoldRateMode = 'M_PER_PLN' | 'PLN_PER_G';
export function defaultGoldRateMode(world: string): GoldRateMode {
  return ['syberia', 'pandora', 'unia'].includes(world.trim().toLocaleLowerCase('pl-PL'))
    ? 'PLN_PER_G'
    : 'M_PER_PLN';
}
export function parseGoldRate(
  input: string,
  mode: GoldRateMode,
): { rateGoldUnits: number; ratePriceMinor: number } | null {
  const value = mode === 'M_PER_PLN' ? parseGoldMillions(input) : parsePln(input);
  return value === null
    ? null
    : mode === 'M_PER_PLN'
      ? { rateGoldUnits: value, ratePriceMinor: 100 }
      : { rateGoldUnits: 1_000_000_000, ratePriceMinor: value };
}
export function goldPackageTotal(
  amount: number,
  goldUnits: number,
  priceMinor: number,
): number | null {
  if (
    ![amount, goldUnits, priceMinor].every((n) => Number.isSafeInteger(n) && n > 0) ||
    amount > 9_000_000_000_000_000 ||
    goldUnits > 9_000_000_000_000_000 ||
    priceMinor > 100_000_000
  )
    return null;
  const divisor = BigInt(goldUnits);
  const total = (BigInt(amount) * BigInt(priceMinor) * 2n + divisor) / (divisor * 2n);
  return total >= 1n && total <= 100_000_000n ? Number(total) : null;
}
export function plnInput(minor: number): string {
  return (
    (BigInt(minor) / 100n).toString() + ',' + (BigInt(minor) % 100n).toString().padStart(2, '0')
  );
}
export function formatGoldRate(
  mode: string | null,
  gold: number | null,
  minor: number | null,
): string {
  if (gold === null || minor === null || gold === undefined || minor === undefined)
    return 'Zaloguj się, aby zobaczyć kurs';
  return mode === 'M_PER_PLN'
    ? '1 PLN = ' + goldMillionsInput(gold) + 'm'
    : mode === 'PLN_PER_G'
      ? '1g = ' + formatPln(minor)
      : formatGold(gold) + ' = ' + formatPln(minor);
}
