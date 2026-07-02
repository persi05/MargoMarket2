export function formatListingPrice(price: number, currencyName: string): string {
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

function formatGoldPrice(price: number): string {
  if (price >= 1_000_000_000) {
    return `${formatCompactNumber(price / 1_000_000_000)}g`;
  }

  if (price >= 1_000_000) {
    return `${formatCompactNumber(price / 1_000_000)}m`;
  }

  return `${new Intl.NumberFormat('pl-PL').format(price)} złota`;
}

function parseGoldPrice(input: string): number | null {
  const normalizedInput = input.trim().toLowerCase().replace(/\s+/g, '').replace(',', '.');
  const unitMatch = normalizedInput.match(/^(\d+(?:\.\d+)?)\s*([mg])$/);

  if (unitMatch) {
    const amount = Number.parseFloat(unitMatch[1]);
    const multiplier = unitMatch[2] === 'g' ? 1_000_000_000 : 1_000_000;
    const value = Math.round(amount * multiplier);
    return Number.isInteger(value) && value > 0 ? value : null;
  }

  if (!/^\d+$/.test(normalizedInput)) {
    return null;
  }

  const value = Number.parseInt(normalizedInput, 10);
  return Number.isInteger(value) && value > 0 ? value : null;
}

function formatCompactNumber(value: number): string {
  if (Number.isInteger(value)) {
    return String(value);
  }

  return value.toFixed(2).replace(/\.?0+$/, '');
}
