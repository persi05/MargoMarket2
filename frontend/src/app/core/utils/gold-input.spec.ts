import { describe, expect, it } from 'vitest';
import { formatGold, parseGoldMillions } from './gold-format';
describe('auction gold input', () => {
  it('formats prices without a redundant gold label', () => {
    expect(formatGold(133)).toBe('133');
    expect(formatGold(200000000)).toBe('200m');
    expect(formatGold(2000000000)).toBe('2g');
  });
  it('accepts decimal separators and gold suffixes without losing precision', () => {
    expect(parseGoldMillions('500m')).toBe(500000000);
    expect(parseGoldMillions('2g')).toBe(2000000000);
    expect(parseGoldMillions('1,5g')).toBe(1500000000);
    expect(parseGoldMillions('1.5g')).toBe(1500000000);
    expect(parseGoldMillions('1,5m')).toBe(1500000);
    expect(parseGoldMillions('1.5')).toBe(1500000);
    expect(parseGoldMillions('0.000000001g')).toBe(1);
    for (const invalid of ['0m','-2g','1,2.3g','2mg','1e3g']) expect(parseGoldMillions(invalid)).toBeNull();
  });
});
