import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { PriceHistoryEntry } from '../../core/models/api.models';
import {
  PriceChartComponent, averageByDay, dayTicks, labelTicks, median, mergeSamePrice, niceStep, priceDomain, soldWord,
  spread, typicalRange
} from './price-chart.component';

function entry(id: number, at: string, plnGrosze: number | null, changes: Partial<PriceHistoryEntry> = {}): PriceHistoryEntry {
  return { listingId: id, source: 'SALE', serverName: 'Classic', enhancement: 0, bound: false, currency: 'PLN',
    amount: plnGrosze ?? 0, plnGrosze, at, ...changes };
}

// Times around noon UTC stay on the same calendar day in any European time zone.
const entries = [
  entry(1, '2026-11-01T12:00:00Z', 10_000),
  entry(2, '2026-11-05T11:00:00Z', 12_000, { source: 'AUCTION', currency: 'GOLD', amount: 2_400_000_000 }),
  entry(3, '2026-11-05T11:05:00Z', 14_000, { enhancement: 3 }),
  entry(4, '2026-11-07T12:00:00Z', 11_000, { enhancement: 3 }),
  entry(7, '2026-11-07T13:00:00Z', 11_000),
  entry(5, '2026-11-10T12:00:00Z', 15_000, { source: 'OFFER' }),
  entry(6, '2026-11-06T12:00:00Z', null)
];
const NOW = Date.parse('2026-11-12T12:00:00Z');

describe('price chart', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render(list = entries) {
    const fixture = TestBed.createComponent(PriceChartComponent);
    (fixture.componentInstance as any).now = () => NOW;
    fixture.componentRef.setInput('entries', list);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement, chart: fixture.componentInstance as any };
  }

  it('draws every sale as its own dot and merges only same-day sales for the same price', async () => {
    const { element, chart } = await render();
    const points = chart.chart().points;
    expect(points.map((p: any) => [p.price, p.count])).toEqual([[10_000, 1], [12_000, 1], [14_000, 1], [11_000, 2]]);
    expect(element.querySelectorAll('circle.dot').length).toBe(4);
    expect(element.querySelectorAll('circle.dot.auction').length).toBe(1);
    // Five minutes apart, yet the two sales of 5.11 sit side by side.
    expect(points[2].x - points[1].x).toBeGreaterThanOrEqual(12);
  });

  it('labels the last price on the right and sums up the sales', async () => {
    const { element, chart } = await render();
    expect(element.querySelector('.last text')!.textContent).toBe('110 zł');
    expect(chart.stats()).toEqual({ median: 11_000, low: 10_000, high: 14_000, count: 5 });
    expect(element.querySelector('.stats')!.textContent).toContain('Sprzedanych5');
  });

  it('shows the price, the kind of sale and the time at the crosshair', async () => {
    const { fixture, element, chart } = await render();
    const auction = chart.chart().points[1];
    element.querySelector('.plot')!.dispatchEvent(new MouseEvent('pointermove', { clientX: auction.x + 2 }));
    fixture.detectChanges();
    const tooltip = element.querySelector('.tooltip')?.textContent ?? '';
    expect(tooltip).toContain('120 zł');
    expect(tooltip).toContain('2,4g');
    expect(tooltip).toContain('1 sprzedany · licytacja · +0');
    expect(tooltip).toMatch(/5 lis 2026, \d\d:00/);
    expect(element.querySelectorAll('line.cross').length).toBe(2);

    const pair = chart.chart().points[3];
    element.querySelector('.plot')!.dispatchEvent(new MouseEvent('pointermove', { clientX: pair.x }));
    fixture.detectChanges();
    expect(element.querySelector('.tooltip')!.textContent).toContain('2 sprzedane');

    element.querySelector('.plot')!.dispatchEvent(new MouseEvent('pointerleave'));
    fixture.detectChanges();
    expect(element.querySelector('.tooltip')).toBeNull();
  });

  it('steps through the dots with the keyboard', async () => {
    const { element, chart } = await render();
    const plot = element.querySelector('.plot') as HTMLElement;
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    expect(chart.active().price).toBe(12_000);
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    expect(chart.active().count).toBe(2);
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(chart.active()).toBeNull();
  });

  it('filters by time range and upgrade level', async () => {
    const { fixture, element, chart } = await render();
    const ranges = Array.from(element.querySelectorAll<HTMLButtonElement>('.ranges button'));
    expect(ranges.map(b => b.textContent?.trim())).toEqual(['1M', '3M', '1R', 'Wszystko']);
    expect(ranges[3].getAttribute('aria-pressed')).toBe('true');
    const levels = Array.from(element.querySelectorAll<HTMLButtonElement>('.chips button'));
    expect(levels.map(b => b.textContent?.trim())).toEqual(['Każde ulepszenie', '+0', '+3']);

    levels[2].click();
    fixture.detectChanges();
    expect(chart.chart().points.map((p: any) => p.price)).toEqual([14_000, 11_000]);

    levels[0].click();
    chart.now = () => Date.parse('2026-12-20T12:00:00Z'); // the sales now lie 6–7 weeks back
    ranges[0].click();
    fixture.detectChanges();
    expect(chart.chart()).toBeNull();
    expect(element.textContent).toContain('Brak sprzedaży w wybranym okresie.');
    ranges[1].click();
    fixture.detectChanges();
    expect(chart.chart().points).toHaveLength(4);
  });

  it('averages whole days only when the sales would not fit side by side', async () => {
    const many = Array.from({ length: 150 }, (_, i) =>
      entry(i + 1, `2026-11-0${(i % 5) + 1}T${String(8 + (i % 10)).padStart(2, '0')}:${String(i % 60).padStart(2, '0')}:00Z`, 10_000 + i * 10));
    const { element, chart } = await render(many);
    const points = chart.chart().points;
    expect(points).toHaveLength(5);
    expect(points.every((p: any) => p.averaged && p.count === 30)).toBe(true);
    expect(element.querySelector('.hint')!.textContent).toContain('średnia z dnia');
  });

  it('keeps a far outlier on the edge instead of squashing the line', async () => {
    const list = Array.from({ length: 12 }, (_, i) => entry(i + 1, `2026-10-${String(i + 10).padStart(2, '0')}T12:00:00Z`, 5_000 + i * 100));
    list.push(entry(99, '2026-11-01T12:00:00Z', 200_000));
    const { element, chart } = await render(list);
    const outlier = chart.chart().points.find((p: any) => p.price === 200_000);
    expect(outlier.clipped).toBe('high');
    expect(outlier.y).toBe(chart.chart().top);
    expect(element.querySelectorAll('path.clip').length).toBe(1);
    expect(chart.stats().high).toBe(200_000);
  });

  it('says when there are no sales at all', async () => {
    const { element } = await render([entry(5, '2026-11-10T12:00:00Z', 15_000, { source: 'OFFER' })]);
    expect(element.textContent).toContain('Brak sprzedaży do pokazania na wykresie.');
    expect(element.querySelector('.stats')).toBeNull();
  });
});

async function renderNarrow(list: PriceHistoryEntry[]) {
  const fixture = TestBed.createComponent(PriceChartComponent);
  (fixture.componentInstance as any).now = () => NOW;
  (fixture.componentInstance as any).width.set(340);
  fixture.componentRef.setInput('entries', list);
  fixture.detectChanges();
  return { chart: fixture.componentInstance as any };
}

describe('price chart helpers', () => {
  afterEach(() => TestBed.resetTestingModule());

  it('merges sales of the same day only when the price is the same', () => {
    const points = mergeSamePrice([entry(1, '2026-11-05T09:00:00Z', 100), entry(2, '2026-11-05T15:00:00Z', 100),
      entry(3, '2026-11-05T16:00:00Z', 300), entry(4, '2026-11-06T12:00:00Z', 100)]);
    expect(points.map(p => [p.price, p.count, p.entry?.listingId ?? null])).toEqual([[100, 2, null], [300, 1, 3], [100, 1, 4]]);
  });

  it('averages a day', () => {
    const [day] = averageByDay([entry(1, '2026-11-05T09:00:00Z', 100), entry(2, '2026-11-05T15:00:00Z', 300)]);
    expect([day.price, day.low, day.high, day.count, day.averaged]).toEqual([200, 100, 300, 2, true]);
  });

  it('never stacks dots on the left edge, even when the space is tight', async () => {
    const list = Array.from({ length: 60 }, (_, i) => entry(i + 1, `2026-11-${String((i % 28) + 1).padStart(2, '0')}T12:00:00Z`, 5_000 + i * 7));
    const { chart } = await renderNarrow(list);
    const xs = chart.chart().points.map((p: any) => p.x);
    for (let i = 1; i < xs.length; i++) expect(xs[i]).toBeGreaterThan(xs[i - 1]);
  });

  it('pushes close dots apart and keeps them inside the plot', () => {
    expect(spread([10, 10, 10, 50], 0, 100, 12)).toEqual([10, 22, 34, 50]);
    expect(spread([90, 95, 100], 0, 100, 12)).toEqual([76, 88, 100]);
  });

  it('writes the number of pieces in Polish', () => {
    expect([1, 2, 4, 5, 12, 22, 25].map(n => `${n} ${soldWord(n)}`))
      .toEqual(['1 sprzedany', '2 sprzedane', '4 sprzedane', '5 sprzedanych', '12 sprzedanych', '22 sprzedane', '25 sprzedanych']);
  });

  it('marks the start of a month with its name', () => {
    const ticks = [new Date(2026, 9, 28).getTime(), new Date(2026, 9, 31).getTime(), new Date(2026, 10, 3).getTime()];
    expect(labelTicks(ticks).map(t => t.label)).toEqual(['28', '31', 'lis']);
  });

  it('picks round grid steps and fits the axis around the prices', () => {
    expect(niceStep(4_125)).toBe(5_000);
    expect(niceStep(10)).toBe(100);
    expect(priceDomain(10_000, 15_000)).toEqual([8_000, 16_000, 2_000]);
    expect(priceDomain(100, 200)[0]).toBe(0);
  });

  it('keeps the range when there is no clear outlier', () => {
    expect(typicalRange([1, 2, 3])).toEqual([1, 3]);
    expect(typicalRange([10, 11, 12, 13, 14, 15, 16, 17, 500])).toEqual([10, 17]);
  });

  it('computes medians and date ticks', () => {
    expect(median([1, 2, 3, 4])).toBe(3);
    const from = new Date(2026, 8, 25, 13).getTime(), to = new Date(2026, 10, 22, 9).getTime();
    for (const max of [4, 8, 12]) {
      const ticks = dayTicks(from, to, max);
      expect(ticks.length).toBeGreaterThanOrEqual(2);
      expect(ticks.length).toBeLessThanOrEqual(max);
      ticks.forEach(t => expect(new Date(t).getHours()).toBe(0));
    }
  });
});
