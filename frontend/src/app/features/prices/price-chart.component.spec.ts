import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { PriceHistoryEntry } from '../../core/models/api.models';
import { PriceChartComponent, niceStep } from './price-chart.component';

function entry(id: number, at: string, plnGrosze: number | null, changes: Partial<PriceHistoryEntry> = {}): PriceHistoryEntry {
  return { listingId: id, source: 'SALE', serverName: 'Classic', enhancement: 0, bound: false, currency: 'PLN',
    amount: plnGrosze ?? 0, plnGrosze, at, ...changes };
}

const entries = [
  entry(3, '2026-11-10T10:00:00Z', 15_000, { enhancement: 3, source: 'OFFER' }),
  entry(1, '2026-11-01T10:00:00Z', 10_000),
  entry(2, '2026-11-05T10:00:00Z', 12_000, { source: 'AUCTION', currency: 'GOLD', amount: 2_400_000_000 }),
  entry(4, '2026-11-06T10:00:00Z', null)
];

describe('price chart', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render(list = entries) {
    const fixture = TestBed.createComponent(PriceChartComponent);
    fixture.componentRef.setInput('entries', list);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement, chart: fixture.componentInstance as any };
  }

  it('draws priced transactions oldest first as a step line', async () => {
    const { element, chart } = await render();
    const points = chart.chart().points;
    expect(points.map((p: any) => p.entry.listingId)).toEqual([1, 2, 3]);
    expect(points[0].x).toBeLessThan(points[1].x);
    expect(points[1].y).toBeLessThan(points[0].y);
    expect(element.querySelectorAll('.dot').length).toBe(3);
    expect(element.querySelector('path.step')?.getAttribute('d')).toMatch(/^M[\d.]+,[\d.]+H[\d.]+V/);
  });

  it('shows what sold, where, when and for how much on hover', async () => {
    const { fixture, element } = await render();
    element.querySelectorAll('.dot')[1].dispatchEvent(new Event('mouseenter'));
    fixture.detectChanges();
    const tooltip = element.querySelector('.tooltip')?.textContent ?? '';
    expect(tooltip).toContain('2,4g');
    expect(tooltip).toContain('≈ 120 zł');
    expect(tooltip).toContain('Licytacja · Classic');
    expect(tooltip).toContain('05.11.2026');
  });

  it('filters by upgrade level', async () => {
    const { fixture, element } = await render();
    const buttons = Array.from(element.querySelectorAll<HTMLButtonElement>('.filters button'));
    expect(buttons.map(b => b.textContent?.trim())).toEqual(['Wszystkie', '+0', '+3']);
    buttons[2].click();
    fixture.detectChanges();
    expect(element.querySelectorAll('.dot').length).toBe(1);
  });

  it('says when there is nothing to draw', async () => {
    const { element } = await render([entry(4, '2026-11-06T10:00:00Z', null)]);
    expect(element.textContent).toContain('Brak cen do pokazania na wykresie.');
  });

  it('fits the price axis to the highest price', async () => {
    const { chart } = await render();
    // Highest 150 zł: 50 zł steps up to 200 zł.
    expect(chart.chart().yTicks.map((t: any) => t.label)).toEqual(['0 zł', '50 zł', '100 zł', '150 zł', '200 zł']);
  });

  it('picks round grid steps', () => {
    expect(niceStep(4_125)).toBe(5_000);
    expect(niceStep(1_500)).toBe(2_000);
    expect(niceStep(10)).toBe(100);
  });
});
