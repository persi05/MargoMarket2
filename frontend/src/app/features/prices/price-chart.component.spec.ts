import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { PriceHistoryEntry } from '../../core/models/api.models';
import { PriceChartComponent, dayTicks, median, niceStep, priceDomain, rollingMedian, typicalRange } from './price-chart.component';

function entry(id: number, at: string, plnGrosze: number | null, changes: Partial<PriceHistoryEntry> = {}): PriceHistoryEntry {
  return { listingId: id, source: 'SALE', serverName: 'Classic', enhancement: 0, bound: false, currency: 'PLN',
    amount: plnGrosze ?? 0, plnGrosze, at, ...changes };
}

const entries = [
  entry(3, '2026-11-10T10:00:00Z', 15_000, { enhancement: 3, source: 'OFFER' }),
  entry(1, '2026-11-01T10:00:00Z', 10_000),
  entry(2, '2026-11-05T10:00:00Z', 12_000, { source: 'AUCTION', currency: 'GOLD', amount: 2_400_000_000 }),
  entry(5, '2026-11-07T10:00:00Z', 11_000, { enhancement: 3 }),
  entry(4, '2026-11-06T10:00:00Z', null)
];
const NOW = Date.parse('2026-11-12T00:00:00Z');

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

  it('draws priced entries oldest first with a shape per kind', async () => {
    const { element, chart } = await render();
    const points = chart.chart().points;
    expect(points.map((p: any) => p.entry.listingId)).toEqual([1, 2, 5, 3]);
    expect(points[0].x).toBeLessThan(points[1].x);
    expect(points[1].y).toBeLessThan(points[0].y);
    expect(element.querySelectorAll('circle.mark.sale').length).toBe(2);
    expect(element.querySelectorAll('path.mark.auction').length).toBe(1);
    expect(element.querySelectorAll('circle.mark.offer').length).toBe(1);
  });

  it('builds the trend and statistics from paid prices only, not from offers', async () => {
    const { element, chart } = await render();
    expect(chart.chart().trend.match(/[ML]/g)).toHaveLength(3);
    expect(chart.stats()).toEqual({ median: 11_000, low: 10_000, high: 12_000, count: 3 });
    const stats = element.querySelector('.stats')!.textContent!;
    expect(stats).toContain('Mediana110 zł');
    expect(stats).toContain('Transakcje3');
    expect(element.querySelector('.median-label')!.textContent).toContain('mediana 110 zł');
  });

  it('shows what sold, where, when and for how much at the crosshair', async () => {
    const { fixture, element, chart } = await render();
    const auction = chart.chart().points[1];
    element.querySelector('.plot')!.dispatchEvent(new MouseEvent('pointermove', { clientX: auction.x + 3 }));
    fixture.detectChanges();
    const tooltip = element.querySelector('.tooltip')?.textContent ?? '';
    expect(tooltip).toContain('2,4g');
    expect(tooltip).toContain('≈ 120 zł');
    expect(tooltip).toContain('Licytacja · Classic');
    expect(tooltip).toContain('05.11.2026');
    expect(element.querySelector('line.crosshair')).not.toBeNull();
    element.querySelector('.plot')!.dispatchEvent(new MouseEvent('pointerleave'));
    fixture.detectChanges();
    expect(element.querySelector('.tooltip')).toBeNull();
  });

  it('steps through the points with the keyboard', async () => {
    const { fixture, element, chart } = await render();
    const plot = element.querySelector('.plot') as HTMLElement;
    expect(plot.getAttribute('tabindex')).toBe('0');
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight' }));
    fixture.detectChanges();
    expect(chart.active().entry.listingId).toBe(2);
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'End' }));
    expect(chart.active().entry.listingId).toBe(3);
    plot.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    expect(chart.active()).toBeNull();
  });

  it('filters by upgrade level and time range', async () => {
    const { fixture, element, chart } = await render();
    const groups = element.querySelectorAll('.controls .chips');
    const ranges = Array.from(groups[0].querySelectorAll<HTMLButtonElement>('button'));
    const levels = Array.from(groups[1].querySelectorAll<HTMLButtonElement>('button'));
    expect(ranges.map(b => b.textContent?.trim())).toEqual(['7 dni', '30 dni', '90 dni', 'Wszystko']);
    expect(levels.map(b => b.textContent?.trim())).toEqual(['Każde ulepszenie', '+0', '+3']);

    levels[2].click();
    fixture.detectChanges();
    expect(chart.chart().points.map((p: any) => p.entry.listingId)).toEqual([5, 3]);

    levels[0].click();
    ranges[0].click(); // last 7 days before 12.11: 5.11 onwards
    fixture.detectChanges();
    expect(chart.chart().points.map((p: any) => p.entry.listingId)).toEqual([2, 5, 3]);
    expect(ranges[0].getAttribute('aria-pressed')).toBe('true');
  });

  it('hides a series from the legend and leaves the others', async () => {
    const { fixture, element, chart } = await render();
    const legend = Array.from(element.querySelectorAll<HTMLButtonElement>('.legend button'));
    expect(legend.map(b => b.textContent?.replace(/\s+/g, ' ').trim())).toEqual(['Sprzedaż 2', 'Licytacja 1', 'Aktualna oferta 1']);
    legend[1].click();
    fixture.detectChanges();
    expect(legend[1].getAttribute('aria-pressed')).toBe('false');
    expect(element.querySelectorAll('.mark.auction').length).toBe(0);
    expect(chart.stats().count).toBe(2);
  });

  it('draws a far outlier on the edge instead of squashing the chart', async () => {
    const list = Array.from({ length: 12 }, (_, i) => entry(i + 1, `2026-11-0${(i % 9) + 1}T10:00:00Z`, 5_000 + i * 100));
    list.push(entry(99, '2026-11-10T10:00:00Z', 200_000));
    const { element, chart } = await render(list);
    const outlier = chart.chart().points.find((p: any) => p.entry.listingId === 99);
    expect(outlier.clipped).toBe('high');
    expect(outlier.y).toBe(chart.chart().top);
    expect(chart.chart().yTicks.at(-1).label).not.toContain('2000');
    expect(element.querySelector('.legend')!.textContent).toContain('1 poza skalą');
    // Statistics still use the real price.
    expect(chart.stats().high).toBe(200_000);
  });

  it('says when there is nothing to draw', async () => {
    const { element } = await render([entry(4, '2026-11-06T10:00:00Z', null)]);
    expect(element.textContent).toContain('Brak cen do pokazania na wykresie.');
    expect(element.querySelector('.stats')).toBeNull();
  });

  it('says when the time range is empty', async () => {
    const { fixture, element } = await render([entry(1, '2026-01-01T10:00:00Z', 10_000)]);
    (element.querySelector('.controls .chips button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(element.textContent).toContain('Brak cen w wybranym okresie.');
  });
});

describe('price chart helpers', () => {
  it('picks round grid steps', () => {
    expect(niceStep(4_125)).toBe(5_000);
    expect(niceStep(1_500)).toBe(2_000);
    expect(niceStep(10)).toBe(100);
  });

  it('fits the price axis around the prices without going below zero', () => {
    expect(priceDomain(10_000, 15_000)).toEqual([8_000, 16_000, 2_000]);
    const [min] = priceDomain(100, 200);
    expect(min).toBe(0);
    const [low, high] = priceDomain(5_000, 5_000);
    expect(high).toBeGreaterThan(low);
  });

  it('keeps the range when there is no clear outlier', () => {
    expect(typicalRange([1, 2, 3])).toEqual([1, 3]);
    expect(typicalRange([10, 11, 12, 13, 14, 15, 16, 17, 18])).toEqual([10, 18]);
    expect(typicalRange([10, 11, 12, 13, 14, 15, 16, 17, 500])).toEqual([10, 17]);
  });

  it('computes medians', () => {
    expect(median([3, 1, 2])).toBe(2);
    expect(median([1, 2, 3, 4])).toBe(3);
    expect(rollingMedian([1, 100, 2, 3, 4], 3)).toEqual([51, 2, 3, 3, 4]);
  });

  it('places at least two midnight date ticks and never too many', () => {
    const from = new Date(2026, 8, 25, 13).getTime(), to = new Date(2026, 10, 22, 9).getTime();
    for (const max of [2, 4, 8, 12]) {
      const ticks = dayTicks(from, to, max);
      expect(ticks.length).toBeGreaterThanOrEqual(2);
      if (max >= 4) expect(ticks.length).toBeLessThanOrEqual(max);
      for (const t of ticks) {
        expect(new Date(t).getHours()).toBe(0);
        expect(t).toBeGreaterThanOrEqual(from);
        expect(t).toBeLessThanOrEqual(to);
      }
    }
  });
});
