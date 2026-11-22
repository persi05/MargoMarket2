import { formatGold, parseDecimalUnits } from './gold-format';
export function formatListingPrice(price: number | null, currencyName: string): string {
  if (price === null) {
    return 'Zaloguj się, aby zobaczyć';
  }

  const normalizedCurrency = currencyName.trim().toLowerCase();

  if (normalizedCurrency === 'w grze') {
    return formatGoldPrice(price);
  }

  const formattedPrice = new Intl.NumberFormat('pl-PL').format(price);

  if (normalizedCurrency === 'pln') {
    return `${formattedPrice} zł`;
  }

  return `${formattedPrice} ${currencyName.trim()}`;
}

export function parseListingPrice(input: string, currencyName: string): number | null {
  const normalizedCurrency = currencyName.trim().toLowerCase();

  if (normalizedCurrency === 'w grze') {
    return parseGoldPrice(input);
  }

  const normalizedInput = input.replace(/\s+/g, '');
  if (!/^\d+$/.test(normalizedInput)) {
    return null;
  }

  const value = Number.parseInt(normalizedInput, 10);
  return Number.isInteger(value) && value > 0 && value <= MAX_PLN_LISTING_PRICE ? value : null;
}

/** Highest gold price of a fixed item listing (10 000g), mirrors ListingRequest.MAX_GOLD_PRICE. */
export const MAX_GOLD_LISTING_PRICE = 10_000_000_000_000;
/** Highest PLN price of a fixed item listing in whole złoty, mirrors ListingRequest.MAX_PLN_PRICE. */
export const MAX_PLN_LISTING_PRICE = 2_000_000_000;

function formatGoldPrice(price: number): string { return formatGold(price); }
/** Accepts 6,5g · 6.5g · 6500m · 6 500m · 6500000000 (plain gold units). */
function parseGoldPrice(input: string): number | null {
 const value=input.trim().toLowerCase().replace(/\s+/g,'');
 const match=value.match(/^(\d+(?:[.,]\d+)?)([mg])$/);
 if(match)return parseDecimalUnits(match[1],match[2]==='m'?6:9,BigInt(MAX_GOLD_LISTING_PRICE));
 if(!/^\d+$/.test(value)||value.length>16)return null;
 const units=Number(value);return Number.isSafeInteger(units)&&units>0&&units<=MAX_GOLD_LISTING_PRICE?units:null;
}

/**
 * Canonical text for a gold price in the listing form: 6,5g from 1g up, 500m from 1m up,
 * plain units below that. parseListingPrice reads every form back to the same amount.
 */
export function goldPriceInput(units: number): string {
 const n=BigInt(units);
 const part=(divisor: bigint, places: number, suffix: string) => {
  const rest=(n%divisor).toString().padStart(places,'0').replace(/0+$/,'');
  return (n/divisor).toString()+(rest?','+rest:'')+suffix;
 };
 return n>=1_000_000_000n?part(1_000_000_000n,9,'g'):n>=1_000_000n?part(1_000_000n,6,'m'):n.toString();
}
