import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, signal } from '@angular/core';

import { PriceHistoryEntry } from '../../core/models/api.models';
import { formatGold, formatPln } from '../../core/utils/gold-format';

export type RangeKey = '1M' | '3M' | '1Y' | 'ALL';

/**
 * A point on the line: one sale, or several sold the same day for the same price ("2 sprzedane").
 * When there are too many to fit, a point is a whole day with its average price (`averaged`).
 */
export interface SalePoint {
  at: number;                        // time of the (first) sale
  price: number;                     // grosze; the average for an averaged day
  low: number;
  high: number;
  count: number;
  averaged: boolean;
  entry: PriceHistoryEntry | null;   // the sale itself when the point is a single sale
  x: number;
  y: number;
  clipped: 'high' | 'low' | null;
}
export type RawPoint = Omit<SalePoint, 'x' | 'y' | 'clipped'>;

interface Chart {
  points: SalePoint[];
  line: string;
  area: string;
  yTicks: { y: number; label: string }[];
  xTicks: { x: number; label: string; month: boolean }[];
  last: { y: number; label: string };
  left: number; right: number; top: number; bottom: number;
}

export interface PriceStats { median: number; low: number; high: number; count: number; }

const HEIGHT = 260;
const PAD = { top: 16, right: 92, bottom: 30, left: 12 };
const DAY = 86_400_000;
/** Points closer than this are pushed apart, so every sale stays visible and hoverable. */
const MIN_GAP = 12;
/** Below this spacing single sales no longer fit, and each day becomes one averaged point. */
const MIN_FIT_GAP = 7;
const RANGES: { key: RangeKey; label: string; days: number | null; title: string }[] = [
  { key: '1M', label: '1M', days: 30, title: 'Ostatni miesiąc' },
  { key: '3M', label: '3M', days: 91, title: 'Ostatnie 3 miesiące' },
  { key: '1Y', label: '1R', days: 365, title: 'Ostatni rok' },
  { key: 'ALL', label: 'Wszystko', days: null, title: 'Cała historia' }
];
const MONTHS = ['sty', 'lut', 'mar', 'kwi', 'maj', 'cze', 'lip', 'sie', 'wrz', 'paź', 'lis', 'gru'];

/**
 * Completed transactions (sales and auctions) of one item, in zł (gold converted by the world rate).
 * Every sale is its own point; sales close in time are pushed apart so each stays visible.
 * Current offers are not drawn, because nobody has paid that price yet.
 */
@Component({
  selector: 'mm-price-chart', standalone: true,
  template: `
    @if (upgradeable() && levels().length > 1) {
      <div class="chips" role="group" aria-label="Ulepszenie na wykresie">
        <button type="button" [attr.aria-pressed]="level() === null" (click)="setLevel(null)">Każde ulepszenie</button>
        @for (n of levels(); track n) {
          <button type="button" [attr.aria-pressed]="level() === n" (click)="setLevel(n)">+{{ n }}</button>
        }
      </div>
    }

    @if (stats(); as s) {
      <dl class="stats">
        <div><dt>Mediana</dt><dd>{{ pln(s.median) }}</dd></div>
        <div><dt>Najniżej</dt><dd>{{ pln(s.low) }}</dd></div>
        <div><dt>Najwyżej</dt><dd>{{ pln(s.high) }}</dd></div>
        <div><dt>Sprzedanych</dt><dd>{{ s.count }}</dd></div>
      </dl>
    }

    <div class="frame">
      @if (chart(); as c) {
        <div class="plot" tabindex="0" role="group"
             [attr.aria-label]="'Wykres cen, ' + c.points.length + ' punktów. Strzałki w lewo i w prawo przechodzą między nimi.'"
             (pointermove)="track($event)" (pointerleave)="active.set(null)" (keydown)="step($event)" (blur)="active.set(null)">
          <svg [attr.width]="width()" [attr.height]="height" aria-hidden="true">
            <defs>
              <linearGradient id="price-fill" x1="0" x2="0" y1="0" y2="1">
                <stop offset="0" stop-color="#3987e5" stop-opacity=".28" /><stop offset="1" stop-color="#3987e5" stop-opacity="0" />
              </linearGradient>
            </defs>
            @for (tick of c.yTicks; track tick.y) {
              <line class="grid" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="tick.y" [attr.y2]="tick.y" />
              <text class="axis" [attr.x]="c.right + 10" [attr.y]="tick.y" dominant-baseline="middle">{{ tick.label }}</text>
            }
            @for (tick of c.xTicks; track tick.x) {
              <line class="grid" [attr.x1]="tick.x" [attr.x2]="tick.x" [attr.y1]="c.top" [attr.y2]="c.bottom" />
              <text class="axis" [class.month]="tick.month" [attr.x]="tick.x" [attr.y]="c.bottom + 20" text-anchor="middle">{{ tick.label }}</text>
            }
            <path class="area" [attr.d]="c.area" />
            <path class="line" [attr.d]="c.line" />
            @if (active(); as a) {
              <line class="cross" [attr.x1]="a.x" [attr.x2]="a.x" [attr.y1]="c.top" [attr.y2]="c.bottom" />
              <line class="cross" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="a.y" [attr.y2]="a.y" />
            }
            @for (p of c.points; track $index) {
              @if (p.clipped) {
                <path class="clip" [class.active]="active() === p" [attr.d]="triangle(p)" />
              } @else {
                <circle class="dot" [class.auction]="p.entry?.source === 'AUCTION'" [class.active]="active() === p"
                        [attr.cx]="p.x" [attr.cy]="p.y" [attr.r]="active() === p ? 6 : p.count > 1 ? 4.5 : 3.5" />
              }
            }
            <g class="last" [attr.transform]="'translate(' + (c.right + 4) + ',' + c.last.y + ')'">
              <rect x="0" y="-10" [attr.width]="padRight - 8" height="20" rx="3" />
              <text x="6" y="0" dominant-baseline="middle">{{ c.last.label }}</text>
            </g>
          </svg>
          @if (active(); as p) {
            <div class="tooltip" role="status" [class]="'tooltip ' + tooltipSide(p)"
                 [style.left.px]="tooltipLeft(p)" [style.top.px]="p.y">
              <strong>{{ pln(p.price) }}</strong>
              @if (p.entry?.currency === 'GOLD') { <span>{{ gold(p.entry!.amount) }}</span> }
              <span>{{ describe(p) }}</span>
              @if (p.low !== p.high) { <span>od {{ pln(p.low) }} do {{ pln(p.high) }}</span> }
              @if (p.clipped) { <span class="note">Poza skalą wykresu</span> }
              <time>{{ p.averaged ? dateLabel(p.at) : dateTimeLabel(p.at) }}</time>
            </div>
          }
        </div>
      } @else {
        <p class="muted">{{ hasSales() ? 'Brak sprzedaży w wybranym okresie.' : 'Brak sprzedaży do pokazania na wykresie.' }}</p>
      }
      <div class="ranges" role="group" aria-label="Zakres czasu">
        @if (averaged()) { <span class="hint">Dużo sprzedaży: punkt to średnia z dnia</span> }
        @for (r of ranges; track r.key) {
          <button type="button" [title]="r.title" [attr.aria-pressed]="range() === r.key" (click)="setRange(r.key)">{{ r.label }}</button>
        }
      </div>
    </div>
  `,
  styles: [`
    :host { display: grid; gap: .75rem; min-width: 0; --line: #3987e5; --auction: #d95926; --surface: var(--color-card-dark, #261c17); }
    .chips { display: flex; flex-wrap: wrap; gap: .35rem; }
    .chips button, .ranges button { min-height: 32px; padding: .3rem .75rem; border: 1px solid var(--color-border); border-radius: 6px;
      background: var(--color-input-dark); color: var(--color-text-muted); font: inherit; font-size: .85rem; font-weight: 700; cursor: pointer; }
    .chips button { border-radius: 999px; }
    .chips button[aria-pressed='true'] { background: rgba(168,201,138,.18); border-color: #729661; color: #d9efbf; }
    .ranges button[aria-pressed='true'] { background: rgba(57,135,229,.18); border-color: var(--line); color: #dbe8fb; }
    button:focus-visible, .plot:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 2px; }
    .stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .5rem; margin: 0; }
    .stats div { padding: .55rem .75rem; border: 1px solid var(--color-border); border-radius: 8px; background: rgba(0,0,0,.14); }
    .stats dt { color: var(--color-text-muted); font-size: .78rem; }
    .stats dd { margin: .15rem 0 0; color: var(--color-text-main); font-size: 1.1rem; font-weight: 800; font-variant-numeric: tabular-nums; }
    .frame { display: grid; gap: .5rem; padding: .75rem .5rem .6rem; border: 1px solid var(--color-border); border-radius: 8px; background: rgba(0,0,0,.18); }
    .plot { position: relative; min-width: 0; touch-action: pan-y; }
    svg { display: block; overflow: visible; }
    .grid { stroke: rgba(151,121,87,.14); }
    .axis { fill: var(--color-text-muted); font-size: 11px; font-variant-numeric: tabular-nums; }
    .axis.month { fill: var(--color-text-main); font-weight: 800; }
    .area { fill: url(#price-fill); }
    .line { fill: none; stroke: var(--line); stroke-width: 2; stroke-linejoin: round; stroke-linecap: round; }
    .dot { fill: var(--line); stroke: var(--surface); stroke-width: 1.5; }
    .dot.auction { fill: var(--auction); }
    .dot.active { stroke: #f1ede4; stroke-width: 2; }
    .clip { fill: var(--line); opacity: .85; }
    .clip.active { stroke: #f1ede4; stroke-width: 1.5; }
    .cross { stroke: var(--color-text-muted); stroke-width: 1; stroke-dasharray: 4 4; opacity: .8; }
    .last rect { fill: var(--line); }
    .last text { fill: #fff; font-size: 11px; font-weight: 800; font-variant-numeric: tabular-nums; }
    .tooltip { position: absolute; z-index: 2; display: grid; gap: 2px; min-width: 150px; padding: .5rem .7rem; pointer-events: none;
      transform: translate(14px, -50%); background: rgba(27,20,17,.94); border: 1px solid var(--color-border); border-radius: 8px;
      box-shadow: 0 6px 18px rgba(0,0,0,.45); font-size: .82rem; color: var(--color-text-main); white-space: nowrap; }
    .tooltip.left { transform: translate(calc(-100% - 14px), -50%); }
    .tooltip.above { transform: translate(-50%, calc(-100% - 14px)); }
    .tooltip strong { font-size: 1rem; font-variant-numeric: tabular-nums; }
    .tooltip time { color: var(--color-text-muted); }
    .note { color: #e0c27a; }
    .ranges { display: flex; justify-content: flex-end; align-items: center; flex-wrap: wrap; gap: .25rem; }
    .hint { margin-right: auto; color: var(--color-text-muted); font-size: .8rem; }
    .muted { margin: 0; padding: 3rem 0; color: var(--color-text-muted); text-align: center; }
    @media (max-width: 640px) { .stats { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
  `]
})
export class PriceChartComponent {
  readonly entries = input.required<PriceHistoryEntry[]>();
  readonly upgradeable = input(true);

  protected readonly height = HEIGHT;
  protected readonly padRight = PAD.right;
  protected readonly ranges = RANGES;
  protected readonly width = signal(720);
  protected readonly level = signal<number | null>(null);
  protected readonly range = signal<RangeKey>('ALL');
  protected readonly active = signal<SalePoint | null>(null);
  /** Injected for tests; ranges are measured back from this moment. */
  now = () => Date.now();

  /** Completed transactions with a price in zł. */
  private readonly sales = computed(() => this.entries()
    .filter(e => e.source !== 'OFFER' && e.plnGrosze !== null && e.plnGrosze > 0));

  protected readonly hasSales = computed(() => this.sales().length > 0);
  protected readonly levels = computed(() => [...new Set(this.sales().map(e => e.enhancement))].sort((a, b) => a - b));

  /** Sales in the chosen range and upgrade level. */
  private readonly filtered = computed(() => {
    const level = this.upgradeable() ? this.level() : null;
    const days = RANGES.find(r => r.key === this.range())!.days;
    const since = days === null ? -Infinity : this.now() - days * DAY;
    return this.sales().filter(e => (level === null || e.enhancement === level) && Date.parse(e.at) >= since);
  });

  readonly stats = computed<PriceStats | null>(() => {
    const prices = this.filtered().map(e => e.plnGrosze!);
    if (!prices.length) return null;
    return { median: median(prices), low: Math.min(...prices), high: Math.max(...prices), count: prices.length };
  });

  /** Every sale on its own; whole days averaged only when the sales would not fit side by side. */
  readonly raw = computed<RawPoint[]>(() => {
    const single = mergeSamePrice(this.filtered());
    const room = Math.max(1, Math.floor((this.width() - PAD.left - PAD.right) / MIN_FIT_GAP));
    return single.length <= room ? single : averageByDay(this.filtered());
  });
  protected readonly averaged = computed(() => this.raw().some(p => p.averaged));

  readonly chart = computed<Chart | null>(() => {
    const raw = this.raw();
    if (!raw.length) return null;

    let from = raw[0].at, to = raw[raw.length - 1].at;
    if (to - from < DAY) { from -= DAY; to += DAY; }
    const span = to - from;
    from -= span * 0.02; to += span * 0.02;

    const [low, high] = typicalRange(raw.map(d => d.price));
    const [yMin, yMax, step] = priceDomain(low, high);
    const left = PAD.left, right = Math.max(left + 40, this.width() - PAD.right), top = PAD.top, bottom = HEIGHT - PAD.bottom;
    const x = (t: number) => left + (t - from) / (to - from) * (right - left);
    const y = (g: number) => bottom - (Math.min(yMax, Math.max(yMin, g)) - yMin) / (yMax - yMin) * (bottom - top);

    const gap = Math.min(MIN_GAP, (right - left) / Math.max(1, raw.length - 1));
    const xs = spread(raw.map(d => x(d.at)), left, right, gap);
    const points: SalePoint[] = raw.map((d, i) => ({ ...d, x: xs[i], y: y(d.price),
      clipped: d.price > yMax ? 'high' : d.price < yMin ? 'low' : null }));
    const line = points.map((p, i) => `${i ? 'L' : 'M'}${p.x.toFixed(1)},${p.y.toFixed(1)}`).join('');
    const area = `${line}L${points[points.length - 1].x.toFixed(1)},${bottom}L${points[0].x.toFixed(1)},${bottom}Z`;
    const yTicks = [];
    for (let v = yMin; v <= yMax + 1; v += step) yTicks.push({ y: y(v), label: formatPln(v) });
    const xTicks = labelTicks(dayTicks(from, to, Math.max(2, Math.floor((right - left) / 70)))).map(t => ({ ...t, x: x(t.at) }));
    const lastPoint = points[points.length - 1];
    return { points, line, area, yTicks, xTicks, last: { y: lastPoint.y, label: formatPln(lastPoint.price) }, left, right, top, bottom };
  });

  constructor() {
    const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
    const destroyRef = inject(DestroyRef);
    afterNextRender(() => {
      const measure = () => { if (host.clientWidth > 0) this.width.set(host.clientWidth - 18); };
      measure();
      if (typeof ResizeObserver === 'undefined') return;
      const observer = new ResizeObserver(measure);
      observer.observe(host);
      destroyRef.onDestroy(() => observer.disconnect());
    });
  }

  protected setRange(key: RangeKey): void { this.range.set(key); this.active.set(null); }
  protected setLevel(level: number | null): void { this.level.set(level); this.active.set(null); }

  /** Crosshair: snaps to the point nearest to the pointer. */
  protected track(event: PointerEvent): void {
    const points = this.chart()?.points;
    if (!points?.length) return;
    const bounds = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const px = event.clientX - bounds.left;
    this.active.set(points.reduce((best, p) => Math.abs(p.x - px) < Math.abs(best.x - px) ? p : best));
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

  /** Right of the dot when it fits, else left, else above it (narrow screens). */
  protected tooltipSide(p: SalePoint): string {
    const box = 190, total = this.width();
    if (p.x + 14 + box <= total) return 'right';
    if (p.x - 14 - box >= 0) return 'left';
    return 'above';
  }

  /** Above the dot the box is centred, but kept inside the chart. */
  protected tooltipLeft(p: SalePoint): number {
    if (this.tooltipSide(p) !== 'above') return p.x;
    return Math.min(Math.max(p.x, 95), this.width() - 95);
  }

  protected triangle(p: SalePoint): string {
    const d = p.clipped === 'high' ? -1 : 1, r = 5, y = p.y + d * 2;
    return `M${p.x},${y + d * r}L${p.x + r},${y - d * r * .4}L${p.x - r},${y - d * r * .4}Z`;
  }

  protected describe(p: SalePoint): string {
    const parts = [(p.averaged && p.count > 1 ? 'średnio, ' : '') + `${p.count} ${soldWord(p.count)}`];
    if (p.entry) {
      parts.push(p.entry.source === 'AUCTION' ? 'licytacja' : 'sprzedaż');
      if (this.upgradeable()) parts.push('+' + p.entry.enhancement);
    }
    return parts.join(' · ');
  }

  protected pln(grosze: number): string { return formatPln(grosze); }
  protected gold(amount: number): string { return formatGold(amount); }
  protected dateLabel(at: number): string {
    const d = new Date(at);
    return `${d.getDate()} ${MONTHS[d.getMonth()]} ${d.getFullYear()}`;
  }
  protected dateTimeLabel(at: number): string {
    const d = new Date(at);
    return `${this.dateLabel(at)}, ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`;
  }
}

/** "1 sprzedany", "2 sprzedane", "5 sprzedanych". */
export function soldWord(count: number): string {
  if (count === 1) return 'sprzedany';
  const tens = count % 100, units = count % 10;
  return units >= 2 && units <= 4 && (tens < 12 || tens > 14) ? 'sprzedane' : 'sprzedanych';
}

function dayOf(time: number): number {
  const d = new Date(time);
  d.setHours(0, 0, 0, 0);
  return d.getTime();
}

/** Each sale is a point, oldest first; sales of the same day for the same price become one point with a count. */
export function mergeSamePrice(entries: PriceHistoryEntry[]): RawPoint[] {
  const sorted = [...entries].sort((a, b) => Date.parse(a.at) - Date.parse(b.at));
  const points: RawPoint[] = [];
  const byKey = new Map<string, RawPoint>();
  for (const e of sorted) {
    const at = Date.parse(e.at), price = e.plnGrosze!;
    const key = dayOf(at) + ':' + price;
    const same = byKey.get(key);
    if (same) { same.count++; same.entry = null; continue; }
    const point: RawPoint = { at, price, low: price, high: price, count: 1, averaged: false, entry: e };
    byKey.set(key, point);
    points.push(point);
  }
  return points;
}

/** One point per day with the average price, for histories too long to show every sale. */
export function averageByDay(entries: PriceHistoryEntry[]): RawPoint[] {
  const byDay = new Map<number, number[]>();
  for (const e of entries) {
    const day = dayOf(Date.parse(e.at));
    byDay.set(day, [...(byDay.get(day) ?? []), e.plnGrosze!]);
  }
  return [...byDay.entries()].sort((a, b) => a[0] - b[0]).map(([day, prices]) => ({
    at: day, price: Math.round(prices.reduce((a, b) => a + b, 0) / prices.length),
    low: Math.min(...prices), high: Math.max(...prices), count: prices.length, averaged: true, entry: null
  }));
}

/**
 * Keeps the time order but pushes points at least `gap` apart (sales minutes apart sit side by side),
 * staying within [left, right]. Assumes the points fit: count × gap ≤ right − left.
 */
export function spread(xs: number[], left: number, right: number, gap: number): number[] {
  const out = [...xs];
  for (let i = 1; i < out.length; i++) out[i] = Math.max(out[i], out[i - 1] + gap);
  if (out.length && out[out.length - 1] > right) {
    out[out.length - 1] = right;
    for (let i = out.length - 2; i >= 0; i--) out[i] = Math.min(out[i], out[i + 1] - gap);
  }
  return out.map(v => Math.max(left, v));
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
  const spreadWidth = Math.max(high - low, high * 0.1, 100);
  const step = niceStep(spreadWidth / 4);
  let min = Math.floor((low - spreadWidth * 0.08) / step) * step;
  const max = Math.ceil((high + spreadWidth * 0.08) / step) * step;
  if (min < 0) min = 0;
  return [min, Math.max(max, min + step), step];
}

/**
 * Price range worth drawing: everything, unless a few prices lie far outside the rest
 * (beyond 3 × IQR from the quartiles). Those sit on the edge instead of squashing the chart.
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

/** Midnight-aligned date ticks every 1, 2, 7, 14, 30 or 60 days: the densest spacing with 2…max ticks. */
export function dayTicks(from: number, to: number, max: number): number[] {
  const limit = Math.max(2, max);
  let sparsestPair: number[] | null = null, any: number[] = [];
  for (const every of [1, 2, 7, 14, 30, 60]) {
    const ticks = midnights(from, to, every);
    if (ticks.length >= 2 && ticks.length <= limit) return ticks;
    if (ticks.length >= 2) sparsestPair = ticks;
    if (ticks.length) any = ticks;
  }
  return sparsestPair ?? any;
}

/** Day numbers, with the month name (bold) where a new month starts. */
export function labelTicks(ticks: number[]): { at: number; label: string; month: boolean }[] {
  return ticks.map((at, i) => {
    const d = new Date(at);
    const newMonth = i > 0 && new Date(ticks[i - 1]).getMonth() !== d.getMonth();
    return { at, label: newMonth ? MONTHS[d.getMonth()] : String(d.getDate()), month: newMonth };
  });
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
