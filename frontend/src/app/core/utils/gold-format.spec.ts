import {
  defaultGoldRateMode,
  parseGoldRate,
  goldPackageTotal,
  formatGoldRate,
} from './gold-format';
import { parseListingPrice, formatListingPrice } from './price-format';
import { parseGoldMillions, formatGold, parsePln, goldMillionsInput } from './gold-format';
describe('Exact gold and PLN units', () => {
  it('keeps existing item-price units compatible', () => {
    expect(parseListingPrice('1,5g', 'w grze')).toBe(1500000000);
    expect(parseListingPrice('0,000000001g', 'w grze')).toBe(1);
    expect(parseListingPrice('3g', 'w grze')).toBeNull();
    expect(formatListingPrice(1500000000, 'w grze')).toBe('1,5g');
    expect(parseListingPrice('150', 'pln')).toBe(150);
  });
  it('converts millions and billions exactly', () => {
    expect(parseGoldMillions('1500')).toBe(1500000000);
    expect(formatGold(1500000000)).toBe('1,5g');
    expect(formatGold(2500000000)).toBe('2,5g');
    expect(parseGoldMillions('0,000001')).toBe(1);
    expect(goldMillionsInput(1500000001)).toBe('1500,000001');
  });
  it('rejects unsafe or ambiguous input', () => {
    for (const s of ['0', '-1', '1e3', 'NaN', '1,0000001', '9000000000,000001', '1.2.3'])
      expect(parseGoldMillions(s), s).toBeNull();
    expect(parseGoldMillions('9000000000')).toBe(9000000000000000);
  });
  it('stores PLN as grosze without rounding', () => {
    expect(parsePln('19,99')).toBe(1999);
    expect(parsePln('0.01')).toBe(1);
    expect(parsePln('1.001')).toBeNull();
    expect(parsePln('1000000')).toBe(100000000);
    expect(parsePln('1000000,01')).toBeNull();
  });
});

describe('Gold exchange-rate pricing', () => {
  it('defaults to PLN per billion on the three worlds', () => {
    for (const world of ['Syberia', 'Pandora', 'Unia'])
      expect(defaultGoldRateMode(world)).toBe('PLN_PER_G');
    expect(defaultGoldRateMode('Aether')).toBe('M_PER_PLN');
  });
  it('calculates total from millions per PLN', () => {
    expect(parseGoldRate('13', 'M_PER_PLN')).toEqual({
      rateGoldUnits: 13000000,
      ratePriceMinor: 100,
    });
    expect(goldPackageTotal(260000000, 13000000, 100)).toBe(2000);
    expect(formatGoldRate('M_PER_PLN', 13000000, 100)).toBe('1 PLN = 13m');
  });
  it('calculates total from PLN per billion', () => {
    expect(parseGoldRate('23', 'PLN_PER_G')).toEqual({
      rateGoldUnits: 1000000000,
      ratePriceMinor: 2300,
    });
    expect(goldPackageTotal(1500000000, 1000000000, 2300)).toBe(3450);
  });
  it('rounds exact half-grosze upward and rejects invalid totals', () => {
    expect(goldPackageTotal(15000000, 1000000000, 100)).toBe(2);
    expect(goldPackageTotal(1, 1000000000, 1)).toBeNull();
    expect(goldPackageTotal(9000000000000000, 1, 100)).toBeNull();
    expect(parseGoldRate('0', 'M_PER_PLN')).toBeNull();
    expect(parseGoldRate('23.001', 'PLN_PER_G')).toBeNull();
  });
});
