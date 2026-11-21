import { describe, expect, it } from 'vitest';
import { goldRateInput, parseGoldRate, parsePlnInput, plnInput } from './world-rates';

describe('world rate inputs', () => {
  it('shows and reads gold per PLN in millions', () => {
    expect(goldRateInput(18_000_000)).toBe('18');
    expect(goldRateInput(43_478_261)).toBe('43,478261');
    expect(goldRateInput(null)).toBe('');
    expect(parseGoldRate('18')).toBe(18_000_000);
    expect(parseGoldRate('18m')).toBe(18_000_000);
    expect(parseGoldRate('0,05g')).toBe(50_000_000);
    expect(parseGoldRate('')).toBeNull();
    expect(parseGoldRate('abc')).toBeUndefined();
  });

  it('shows and reads PLN prices in grosze', () => {
    expect(plnInput(15_000)).toBe('150');
    expect(plnInput(50)).toBe('0,5');
    expect(plnInput(null)).toBe('');
    expect(parsePlnInput('150')).toBe(15_000);
    expect(parsePlnInput('0,5 zł')).toBe(50);
    expect(parsePlnInput(' ')).toBeNull();
    expect(parsePlnInput('-1')).toBeUndefined();
  });
});
