import { describe, expect, it } from 'vitest';
import { auctionAmount, auctionRemaining } from './auction-format';
describe('auction display', () => {
  it('keeps money and gold units separate and private values hidden', () => {
    expect(auctionAmount(3450,'PLN')).toContain('34,5 PLN');
    expect(auctionAmount(1500000000,'GOLD')).toBe('1,5g');
    expect(auctionAmount(null,'PLN')).toContain('Zaloguj');
  });
  it('uses server time and closes exactly at the deadline', () => {
    const end='2026-11-13T12:00:00Z';
    expect(auctionRemaining(end,Date.parse(end))).toBe('Czas minął');
    expect(auctionRemaining(end,Date.parse(end)-1000)).toBe('0 min 1 s');
    expect(auctionRemaining(end,Date.parse(end)-120000)).toBe('2 min 0 s');
  });
});
