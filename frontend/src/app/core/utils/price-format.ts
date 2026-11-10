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
    return `${formattedPrice} PLN`;
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
  return Number.isInteger(value) && value > 0 ? value : null;
}

function formatGoldPrice(price: number): string { return formatGold(price); }
function parseGoldPrice(input: string): number | null {
 const value=input.trim().toLowerCase().replace(/\s+/g,'');
 const match=value.match(/^(\d+(?:[.,]\d+)?)([mg])$/);
 if(match)return parseDecimalUnits(match[1],match[2]==='m'?6:9,2_000_000_000n);
 if(!/^\d+$/.test(value)||value.length>10)return null;
 const units=Number(value);return Number.isSafeInteger(units)&&units>0&&units<=2_000_000_000?units:null;
}
