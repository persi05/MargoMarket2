import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, signal } from '@angular/core';

import { PriceHistoryEntry } from '../../core/models/api.models';
import { formatGold, formatPln } from '../../core/utils/gold-format';

export type Series = 'SALE' | 'AUCTION' | 'OFFER';
/** `clipped` marks a price outside the drawn range: it sits on the edge as a triangle, the tooltip keeps the real value. */
export interface ChartPoint { entry: PriceHistoryEntry; time: number; x: number; y: number; clipped: 'high' | 'low' | null; }
export type RangeDays = 7 | 30 | 90 | null;

interface Chart {
  points: ChartPoint[];
  clippedCount: number;
  trend: string;
  median: { y: number; label: string } | null;
  yTicks: { y: number; label: string }[];
  xTicks: { x: number; at: Date }[];
  left: number; right: number; top: number; bottom: number;
}

export interface PriceStats { median: number; low: number; high: number; count: number; }

const HEIGHT = 300;
const PAD = { top: 16, right: 20, bottom: 34, left: 72 };
const DAY = 86_400_000;
/** Validated (dataviz validator, dark surface): all pairs pass CVD and normal-vision checks. */
export const SERIES: { id: Series; label: string }[] = [
  { id: 'SALE', label: 'Sprzedaż' }, { id: 'AUCTION', label: 'Licytacja' }, { id: 'OFFER', label: 'Aktualna oferta' }
];
const RANGES: { days: RangeDays; label: string }[] = [
  { days: 7, label: '7 dni' }, { days: 30, label: '30 dni' }, { days: 90, label: '90 dni' }, { days: null, label: 'Wszystko' }
];

/**
 * Prices observed for one item over time (in zł; gold converted by the world rate).
 * Completed transactions (sales, auctions) carry the trend line and the statistics;
 * current offers are shown as hollow rings because nobody has paid that price yet.
 */
@Component({
  selector: 'mm-price-chart', standalone: true, imports: [DatePipe],
  template: `
    <div class="controls">
      <div class="chips" role="group" aria-label="Zakres czasu">
        @for (r of ranges; track r.label) {
          <button type="button" [attr.aria-pressed]="range() === r.days" (click)="setRange(r.days)">{{ r.label }}</button>
        }
      </div>
      @if (upgradeable() && levels().length > 1) {
        <div class="chips" role="group" aria-label="Ulepszenie na wykresie">
          <button type="button" [attr.aria-pressed]="level() === null" (click)="level.set(null)">Każde ulepszenie</button>
          @for (n of levels(); track n) {
            <button type="button" [attr.aria-pressed]="level() === n" (click)="level.set(n)">+{{ n }}</button>
          }
        </div>
      }
    </div>

    @if (stats(); as s) {
      <dl class="stats">
        <div><dt>Mediana</dt><dd>{{ pln(s.median) }}</dd></div>
        <div><dt>Najniżej</dt><dd>{{ pln(s.low) }}</dd></div>
        <div><dt>Najwyżej</dt><dd>{{ pln(s.high) }}</dd></div>
        <div><dt>Transakcje</dt><dd>{{ s.count }}</dd></div>
      </dl>
    }

    @if (chart(); as c) {
      <div class="plot" tabindex="0" role="group" [attr.aria-label]="'Wykres cen, ' + c.points.length + ' punktów. Strzałki w lewo i w prawo przechodzą między punktami.'"
           (pointermove)="track($event)" (pointerleave)="active.set(null)" (keydown)="step($event)" (blur)="active.set(null)">
        <svg [attr.width]="width()" [attr.height]="height" aria-hidden="true">
          <defs>
            <linearGradient id="trend-fill" x1="0" x2="0" y1="0" y2="1">
              <stop offset="0" stop-color="#199e70" stop-opacity=".22" /><stop offset="1" stop-color="#199e70" stop-opacity="0" />
            </linearGradient>
          </defs>
          @for (tick of c.yTicks; track tick.y) {
            <line class="grid" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="tick.y" [attr.y2]="tick.y" />
            <text class="axis" [attr.x]="c.left - 10" [attr.y]="tick.y" text-anchor="end" dominant-baseline="middle">{{ tick.label }}</text>
          }
          @for (tick of c.xTicks; track tick.x) {
            <line class="tick" [attr.x1]="tick.x" [attr.x2]="tick.x" [attr.y1]="c.bottom" [attr.y2]="c.bottom + 4" />
            <text class="axis" [attr.x]="tick.x" [attr.y]="c.bottom + 20" text-anchor="middle">{{ tick.at | date:'d.MM' }}</text>
          }
          <line class="base" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="c.bottom" [attr.y2]="c.bottom" />
          @if (c.trend) {
            <path class="area" [attr.d]="c.trend + 'V' + c.bottom + 'H' + trendStart(c) + 'Z'" />
            <path class="trend" [attr.d]="c.trend" />
          }
          @if (c.median; as m) {
            <line class="median" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="m.y" [attr.y2]="m.y" />
            <text class="median-label" [attr.x]="c.left + 8" [attr.y]="m.y - 7" text-anchor="start">mediana {{ m.label }}</text>
          }
          @if (active(); as a) {
            <line class="crosshair" [attr.x1]="a.x" [attr.x2]="a.x" [attr.y1]="c.top" [attr.y2]="c.bottom" />
          }
          @for (point of c.points; track point.entry.listingId + '-' + point.entry.at) {
            @if (point.clipped) {
              <path [class]="'mark clipped ' + point.entry.source.toLowerCase()" [class.active]="active() === point"
                    [attr.d]="triangle(point.x, point.y, point.clipped, active() === point ? 8 : 6)" />
            } @else {
            @switch (point.entry.source) {
              @case ('AUCTION') {
                <path class="mark auction" [class.active]="active() === point"
                      [attr.d]="diamond(point.x, point.y, active() === point ? 8 : 6)" />
              }
              @case ('OFFER') {
                <circle class="mark offer" [class.active]="active() === point" [attr.cx]="point.x" [attr.cy]="point.y" [attr.r]="active() === point ? 7 : 5" />
              }
              @default {
                <circle class="mark sale" [class.active]="active() === point" [attr.cx]="point.x" [attr.cy]="point.y" [attr.r]="active() === point ? 7 : 5" />
              }
            }
            }
          }
        </svg>
        @if (active(); as point) {
          <div class="tooltip" role="status" [class.flip]="point.x > width() - 180" [class.start]="point.x < 100" [class.below]="point.y < 100"
               [style.left.px]="point.x" [style.top.px]="point.y">
            <strong>{{ amount(point.entry) }}</strong>
            @if (point.entry.currency === 'GOLD' && point.entry.plnGrosze !== null) { <span>≈ {{ pln(point.entry.plnGrosze) }}</span> }
            @if (point.clipped) { <span class="note">{{ point.clipped === 'high' ? 'Poza skalą: znacznie powyżej typowych cen' : 'Poza skalą: znacznie poniżej typowych cen' }}</span> }
            <span class="row"><i [class]="'key ' + point.entry.source.toLowerCase()"></i>{{ source(point.entry) }} · {{ point.entry.serverName }}</span>
            @if (detail(point.entry); as d) { <span>{{ d }}</span> }
            <time>{{ point.entry.at | date:'dd.MM.yyyy HH:mm' }}</time>
          </div>
        }
      </div>
    } @else {
      <p class="muted">{{ hasPrices() ? 'Brak cen w wybranym okresie.' : 'Brak cen do pokazania na wykresie.' }}</p>
    }

    <div class="legend" role="group" aria-label="Serie na wykresie">
      @for (s of series; track s.id) {
        <button type="button" [attr.aria-pressed]="!hidden().has(s.id)" (click)="toggle(s.id)">
          <i [class]="'key ' + s.id.toLowerCase()"></i>{{ s.label }} <span class="count">{{ counts()[s.id] }}</span>
        </button>
      }
      @if (chart()?.clippedCount) { <span class="trend-key">▲ {{ chart()!.clippedCount }} poza skalą (najedź, aby zobaczyć cenę)</span> }
      @if (chart()?.trend) { <span class="trend-key"><i></i>Trend (mediana z 5 kolejnych transakcji)</span> }
    </div>
  `,
  styles: [`
    :host { display: grid; gap: .75rem; min-width: 0; --sale: #199e70; --auction: #d95926; --offer: #3987e5; --surface: var(--color-card-dark, #261c17); }
    .controls { display: flex; flex-wrap: wrap; gap: .5rem 1rem; }
    .chips { display: flex; flex-wrap: wrap; gap: .35rem; }
    .chips button, .legend button { min-height: 32px; padding: .3rem .75rem; border: 1px solid var(--color-border); border-radius: 999px;
      background: var(--color-input-dark); color: var(--color-text-muted); font: inherit; font-size: .85rem; font-weight: 700; cursor: pointer; }
    .chips button[aria-pressed='true'] { background: rgba(168,201,138,.18); border-color: #729661; color: #d9efbf; }
    .chips button:focus-visible, .legend button:focus-visible, .plot:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
    .stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .5rem; margin: 0; }
    .stats div { padding: .55rem .75rem; border: 1px solid var(--color-border); border-radius: 8px; background: rgba(0,0,0,.14); }
    .stats dt { color: var(--color-text-muted); font-size: .78rem; }
    .stats dd { margin: .15rem 0 0; color: var(--color-text-main); font-size: 1.1rem; font-weight: 800; font-variant-numeric: tabular-nums; }
    .plot { position: relative; min-width: 0; border-radius: 6px; touch-action: pan-y; }
    svg { display: block; overflow: visible; }
    .grid { stroke: rgba(151,121,87,.16); } .base { stroke: rgba(151,121,87,.5); } .tick { stroke: rgba(151,121,87,.5); }
    .axis { fill: var(--color-text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
    .area { fill: url(#trend-fill); }
    .trend { fill: none; stroke: var(--sale); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .median { stroke: var(--color-text-muted); stroke-width: 1; stroke-dasharray: 4 4; opacity: .7; }
    .median-label { fill: var(--color-text-muted); font-size: 11px; font-weight: 700; paint-order: stroke; stroke: var(--surface); stroke-width: 3px; }
    .crosshair { stroke: var(--color-text-muted); stroke-width: 1; opacity: .6; }
    .mark { stroke: var(--surface); stroke-width: 2; }
    .mark.sale { fill: var(--sale); } .mark.auction { fill: var(--auction); }
    .mark.offer { fill: var(--surface); stroke: var(--offer); stroke-width: 2; }
    .mark.active { stroke: #f1ede4; }
    .mark.offer.active { stroke: var(--offer); stroke-width: 3; }
    .mark.clipped.offer { fill: var(--offer); stroke: var(--surface); }
    .note { color: #e0c27a; }
    .tooltip { position: absolute; z-index: 2; display: grid; gap: 2px; min-width: 160px; padding: .55rem .7rem; pointer-events: none;
      transform: translate(-50%, calc(-100% - 16px)); background: #1b1411; border: 1px solid var(--color-border); border-radius: 8px;
      box-shadow: 0 6px 18px rgba(0,0,0,.45); font-size: .82rem; color: var(--color-text-muted); white-space: nowrap; }
    .tooltip.flip { transform: translate(calc(-100% + 18px), calc(-100% - 16px)); }
    .tooltip.start { transform: translate(-18px, calc(-100% - 16px)); }
    .tooltip.below { transform: translate(-50%, 16px); } .tooltip.below.flip { transform: translate(calc(-100% + 18px), 16px); }
    .tooltip.below.start { transform: translate(-18px, 16px); }
    .tooltip strong { color: var(--color-text-main); font-size: 1rem; font-variant-numeric: tabular-nums; }
    .row { display: flex; align-items: center; gap: .4rem; }
    .legend { display: flex; flex-wrap: wrap; align-items: center; gap: .4rem; }
    .legend button { display: inline-flex; align-items: center; gap: .4rem; color: var(--color-text-main); font-weight: 600; }
    .legend button[aria-pressed='false'] { opacity: .45; text-decoration: line-through; }
    .count { color: var(--color-text-muted); font-variant-numeric: tabular-nums; }
    .key { display: inline-block; flex: none; width: 10px; height: 10px; border-radius: 50%; }
    .key.sale { background: var(--sale); } .key.auction { background: var(--auction); border-radius: 1px; transform: rotate(45deg) scale(.85); }
    .key.offer { border: 2px solid var(--offer); }
    .trend-key { display: inline-flex; align-items: center; gap: .4rem; margin-left: .25rem; color: var(--color-text-muted); font-size: .82rem; }
    .trend-key i { display: inline-block; width: 16px; height: 2px; background: var(--sale); border-radius: 1px; }
    .muted { margin: 0; padding: 2rem 0; color: var(--color-text-muted); text-align: center; }
    @media (max-width: 640px) { .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
    @media (prefers-reduced-motion: no-preference) { .mark { transition: r .1s; } }
  `]
})
export class PriceChartComponent {
  readonly entries = input.required<PriceHistoryEntry[]>();
  readonly upgradeable = input(true);

  protected readonly height = HEIGHT;
  protected readonly series = SERIES;
  protected readonly ranges = RANGES;
  protected readonly width = signal(720);
  protected readonly level = signal<number | null>(null);
  protected readonly range = signal<RangeDays>(null);
  protected readonly hidden = signal<ReadonlySet<Series>>(new Set());
  protected readonly active = signal<ChartPoint | null>(null);
  /** Injected for tests; the chart measures ranges back from this moment. */
  now = () => Date.now();

  protected readonly hasPrices = computed(() => this.entries().some(e => e.plnGrosze !== null && e.plnGrosze > 0));
  protected readonly levels = computed(() => [...new Set(this.entries().map(e => e.enhancement))].sort((a, b) => a - b));

  /** Priced entries that pass the time range and upgrade filters, oldest first (before the series toggle). */
  private readonly filtered = computed(() => {
    const level = this.upgradeable() ? this.level() : null;
    const days = this.range();
    const since = days === null ? -Infinity : this.now() - days * DAY;
    return this.entries()
      .filter(e => e.plnGrosze !== null && e.plnGrosze > 0 && (level === null || e.enhancement === level))
      .map(entry => ({ entry, time: Date.parse(entry.at) }))
      .filter(d => d.time >= since)
      .sort((a, b) => a.time - b.time);
  });

  protected readonly counts = computed(() => {
    const counts: Record<Series, number> = { SALE: 0, AUCTION: 0, OFFER: 0 };
    this.filtered().forEach(d => counts[d.entry.source]++);
    return counts;
  });

  /** Statistics of completed transactions only; offers are asking prices, not paid ones. */
  readonly stats = computed<PriceStats | null>(() => {
    const paid = this.filtered().filter(d => d.entry.source !== 'OFFER' && !this.hidden().has(d.entry.source)).map(d => d.entry.plnGrosze!);
    if (!paid.length) return null;
    return { median: median(paid), low: Math.min(...paid), high: Math.max(...paid), count: paid.length };
  });

  readonly chart = computed<Chart | null>(() => {
    const data = this.filtered().filter(d => !this.hidden().has(d.entry.source));
    if (!data.length) return null;

    let from = data[0].time, to = data[data.length - 1].time;
    if (to - from < DAY) { from -= DAY / 2; to += DAY / 2; }
    const span = to - from;
    from -= span * 0.03; to += span * 0.03;

    const prices = data.map(d => d.entry.plnGrosze!);
    const [low, high] = typicalRange(prices);
    const [yMin, yMax, step] = priceDomain(low, high);
    const left = this.width() < 480 ? 54 : PAD.left, right = Math.max(left + 40, this.width() - PAD.right), top = PAD.top, bottom = HEIGHT - PAD.bottom;
    const x = (time: number) => left + (time - from) / (to - from) * (right - left);
    const y = (grosze: number) => bottom - (grosze - yMin) / (yMax - yMin) * (bottom - top);

    const points: ChartPoint[] = data.map(d => {
      const price = d.entry.plnGrosze!;
      const clipped = price > yMax ? 'high' : price < yMin ? 'low' : null;
      return { entry: d.entry, time: d.time, x: x(d.time), y: clipped === 'high' ? top : clipped === 'low' ? bottom : y(price), clipped };
    });
    const paid = points.filter(p => p.entry.source !== 'OFFER');
    const trend = paid.length >= 3
      ? rollingMedian(paid.map(p => p.entry.plnGrosze!), 5)
          .map((v, i) => `${i ? 'L' : 'M'}${paid[i].x.toFixed(1)},${y(Math.min(yMax, Math.max(yMin, v))).toFixed(1)}`).join('')
      : '';
    const stats = this.stats();
    const medianLine = stats && paid.length >= 2 ? { y: y(stats.median), label: formatPln(stats.median) } : null;
    const yTicks = [];
    for (let v = yMin; v <= yMax + 1; v += step) yTicks.push({ y: y(v), label: formatPln(v) });
    const xTicks = dayTicks(from, to, Math.max(2, Math.floor((right - left) / 80))).map(t => ({ x: x(t), at: new Date(t) }));
    return { points, clippedCount: points.filter(p => p.clipped).length, trend, median: medianLine, yTicks, xTicks, left, right, top, bottom };
  });

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const measure = () => { if (host.clientWidth > 0) this.width.set(host.clientWidth); };
      measure();
      if (typeof ResizeObserver === 'undefined') return;
      const observer = new ResizeObserver(measure);
      observer.observe(host);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  protected setRange(days: RangeDays): void { this.range.set(days); this.active.set(null); }

  protected toggle(series: Series): void {
    const next = new Set(this.hidden());
    if (next.has(series)) next.delete(series); else next.add(series);
    this.hidden.set(next);
    this.active.set(null);
  }

  /** Crosshair: snaps to the point nearest to the pointer along the time axis. */
  protected track(event: PointerEvent): void {
    const chart = this.chart();
    if (!chart) return;
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.active.set(nearest(chart.points, event.clientX - bounds.left));
  }

  protected step(event: KeyboardEvent): void {
    const points = this.chart()?.points;
    if (!points?.length || !['ArrowLeft', 'ArrowRight', 'Home', 'End', 'Escape'].includes(event.key)) return;
    event.preventDefault();
    if (event.key === 'Escape') { this.active.set(null); return; }
    const current = this.active() ? points.indexOf(this.active()!) : -1;
    const index = event.key === 'Home' ? 0 : event.key === 'End' ? points.length - 1
      : current < 0 ? (event.key === 'ArrowLeft' ? points.length - 1 : 0)
      : Math.min(points.length - 1, Math.max(0, current + (event.key === 'ArrowLeft' ? -1 : 1)));
    this.active.set(points[index]);
  }

  protected trendStart(chart: Chart): string {
    return chart.trend.slice(1, chart.trend.indexOf(','));
  }

  protected triangle(cx: number, cy: number, side: 'high' | 'low', r: number): string {
    const dir = side === 'high' ? -1 : 1;
    return `M${cx},${cy + dir * r}L${cx + r},${cy - dir * r * .6}L${cx - r},${cy - dir * r * .6}Z`;
  }

  protected diamond(cx: number, cy: number, r: number): string {
    return `M${cx},${cy - r}L${cx + r},${cy}L${cx},${cy + r}L${cx - r},${cy}Z`;
  }

  protected pln(grosze: number): string { return formatPln(grosze); }
  protected amount(entry: PriceHistoryEntry): string {
    return entry.currency === 'GOLD' ? formatGold(entry.amount) : formatPln(entry.amount);
  }
  protected source(entry: PriceHistoryEntry): string {
    return entry.source === 'SALE' ? 'Sprzedaż' : entry.source === 'AUCTION' ? 'Licytacja' : 'Aktualna oferta';
  }
  protected detail(entry: PriceHistoryEntry): string {
    return [this.upgradeable() ? '+' + entry.enhancement : '', entry.bound ? 'związany' : ''].filter(Boolean).join(' · ');
  }
}

/** Grid step in grosze: 1, 2 or 5 times a power of ten, at least 1 zł. */
export function niceStep(grosze: number): number {
  const raw = Math.max(100, grosze);
  const power = 10 ** Math.floor(Math.log10(raw));
  const factor = raw / power;
  return (factor <= 1 ? 1 : factor <= 2 ? 2 : factor <= 5 ? 5 : 10) * power;
}

/** Y range rounded to grid steps around the prices, so small changes stay visible; never below 0. */
export function priceDomain(low: number, high: number): [number, number, number] {
  const spread = Math.max(high - low, high * 0.1, 100);
  const step = niceStep(spread / 4);
  let min = Math.floor((low - spread * 0.08) / step) * step;
  const max = Math.ceil((high + spread * 0.08) / step) * step;
  if (min < 0) min = 0;
  return [min, Math.max(max, min + step), step];
}

/**
 * Price range worth drawing: everything, unless a few prices lie far outside the rest
 * (beyond 3 × IQR from the quartiles). Those are drawn on the edge instead of squashing the chart.
 */
export function typicalRange(prices: number[]): [number, number] {
  const sorted = [...prices].sort((a, b) => a - b);
  const low = sorted[0], high = sorted[sorted.length - 1];
  if (sorted.length < 8) return [low, high];
  const q = (f: number) => sorted[Math.min(sorted.length - 1, Math.floor(f * (sorted.length - 1)))];
  const q1 = q(0.25), q3 = q(0.75), fence = 3 * Math.max(q3 - q1, q3 * 0.05);
  const inside = sorted.filter(v => v >= q1 - fence && v <= q3 + fence);
  return [inside[0] ?? low, inside[inside.length - 1] ?? high];
}

export function median(values: number[]): number {
  const sorted = [...values].sort((a, b) => a - b);
  const mid = sorted.length >> 1;
  return sorted.length % 2 ? sorted[mid] : Math.round((sorted[mid - 1] + sorted[mid]) / 2);
}

/** Centred rolling median; the window shrinks at the edges. */
export function rollingMedian(values: number[], window: number): number[] {
  const half = window >> 1;
  return values.map((_, i) => median(values.slice(Math.max(0, i - half), i + half + 1)));
}

/** Midnight-aligned date ticks every 1, 2, 7, 14, 30 or 60 days: the densest spacing with 2…max ticks (at least 2 when possible). */
export function dayTicks(from: number, to: number, max: number): number[] {
  const limit = Math.max(2, max);
  let sparsestPair: number[] | null = null, any: number[] = [];
  for (const every of [1, 2, 7, 14, 30, 60]) {
    const ticks = midnights(from, to, every);
    if (ticks.length >= 2 && ticks.length <= limit) return ticks;
    if (ticks.length >= 2) sparsestPair = ticks;
    if (ticks.length) any = ticks;
  }
  // Two dates are worth more than a strict limit: a single date gives no sense of scale.
  return sparsestPair ?? any;
}

function midnights(from: number, to: number, every: number): number[] {
  const start = new Date(from);
  start.setHours(0, 0, 0, 0);
  const ticks: number[] = [];
  for (let day = 0; ; day += every) {
    const tick = new Date(start);
    tick.setDate(start.getDate() + day); // calendar days, so ticks stay on midnight across DST changes
    if (tick.getTime() > to) return ticks;
    if (tick.getTime() >= from) ticks.push(tick.getTime());
  }
}

function nearest(points: ChartPoint[], x: number): ChartPoint | null {
  let best: ChartPoint | null = null;
  for (const p of points) if (!best || Math.abs(p.x - x) < Math.abs(best.x - x)) best = p;
  return best;
}
