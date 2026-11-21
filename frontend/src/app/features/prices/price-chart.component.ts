import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, afterNextRender, computed, inject, input, signal } from '@angular/core';

import { PriceHistoryEntry } from '../../core/models/api.models';
import { formatGold, formatPln } from '../../core/utils/gold-format';

export interface ChartPoint { entry: PriceHistoryEntry; x: number; y: number; }

interface Chart {
  points: ChartPoint[];
  path: string;
  yTicks: { y: number; label: string }[];
  xTicks: { x: number; at: Date }[];
  left: number; right: number; top: number; bottom: number;
}

const HEIGHT = 260;
const PAD = { top: 14, right: 18, bottom: 34, left: 70 };
const DAY = 86_400_000;

/** Step chart of observed prices (in zł) over time; hovering a dot shows what was sold, where, when and for how much. */
@Component({
  selector: 'mm-price-chart', standalone: true, imports: [DatePipe],
  template: `
    @if (upgradeable() && levels().length > 1) {
      <div class="filters" role="group" aria-label="Ulepszenie na wykresie">
        <button type="button" [attr.aria-pressed]="level() === null" (click)="level.set(null)">Wszystkie</button>
        @for (n of levels(); track n) {
          <button type="button" [attr.aria-pressed]="level() === n" (click)="level.set(n)">+{{ n }}</button>
        }
      </div>
    }
    @if (chart(); as c) {
      <div class="plot">
        <svg [attr.width]="width()" [attr.height]="height" role="img" [attr.aria-label]="'Wykres cen: ' + c.points.length + ' transakcji'">
          @for (tick of c.yTicks; track tick.y) {
            <line class="grid" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="tick.y" [attr.y2]="tick.y" />
            <text class="axis" [attr.x]="c.left - 8" [attr.y]="tick.y" text-anchor="end" dominant-baseline="middle">{{ tick.label }}</text>
          }
          @for (tick of c.xTicks; track tick.x) {
            <text class="axis" [attr.x]="tick.x" [attr.y]="c.bottom + 20" [attr.text-anchor]="$first ? 'start' : $last ? 'end' : 'middle'">{{ tick.at | date:'dd.MM.yy' }}</text>
          }
          <line class="base" [attr.x1]="c.left" [attr.x2]="c.right" [attr.y1]="c.bottom" [attr.y2]="c.bottom" />
          <path class="step" [attr.d]="c.path" />
          @for (point of c.points; track point.entry.listingId + '-' + point.entry.at) {
            <g class="dot" [class.active]="active() === point" tabindex="0" [attr.aria-label]="describe(point.entry)"
               (mouseenter)="active.set(point)" (mouseleave)="active.set(null)" (focus)="active.set(point)" (blur)="active.set(null)">
              <circle class="hit" [attr.cx]="point.x" [attr.cy]="point.y" r="11" />
              <circle [class]="'mark ' + point.entry.source.toLowerCase()" [attr.cx]="point.x" [attr.cy]="point.y" [attr.r]="active() === point ? 7 : 5" />
            </g>
          }
        </svg>
        @if (active(); as point) {
          <div class="tooltip" role="status" [class.flip]="point.x > width() - 170" [class.start]="point.x < 90" [class.below]="point.y < 90"
               [style.left.px]="point.x" [style.top.px]="point.y">
            <strong>{{ amount(point.entry) }}</strong>
            @if (point.entry.currency === 'GOLD' && point.entry.plnGrosze !== null) { <span>≈ {{ pln(point.entry.plnGrosze) }}</span> }
            <span>{{ source(point.entry) }} · {{ point.entry.serverName }}</span>
            <span>{{ upgradeable() ? '+' + point.entry.enhancement : '' }}{{ point.entry.bound ? (upgradeable() ? ' · ' : '') + 'związany' : '' }}</span>
            <time>{{ point.entry.at | date:'dd.MM.yyyy HH:mm' }}</time>
          </div>
        }
      </div>
      <p class="legend"><span class="key sale"></span>Sprzedaż <span class="key auction"></span>Licytacja <span class="key offer"></span>Aktualna oferta</p>
    } @else {
      <p class="muted">Brak cen do pokazania na wykresie.</p>
    }
  `,
  styles: [`
    :host { display: grid; gap: .6rem; min-width: 0; }
    .filters { display: flex; flex-wrap: wrap; gap: .4rem; }
    .filters button { padding: .3rem .7rem; border: 1px solid var(--color-border); border-radius: 999px; background: var(--color-input-dark);
      color: var(--color-text-main); font: inherit; font-size: .85rem; font-weight: 700; cursor: pointer; }
    .filters button[aria-pressed='true'] { background: rgba(168,201,138,.2); border-color: #729661; color: #d9efbf; }
    .plot { position: relative; min-width: 0; }
    svg { display: block; overflow: visible; }
    .grid { stroke: rgba(151,121,87,.2); } .base { stroke: rgba(151,121,87,.5); }
    .axis { fill: var(--color-text-muted); font-size: 11px; }
    .step { fill: none; stroke: #b4ce93; stroke-width: 2; stroke-linejoin: round; }
    .dot { cursor: pointer; outline: none; }
    .hit { fill: transparent; }
    .mark { stroke: #1b1411; stroke-width: 2; transition: r .1s; }
    .mark.sale { fill: #b4ce93; } .mark.auction { fill: #e0c27a; } .mark.offer { fill: #1b1411; stroke: #8fb3d9; }
    .dot:focus-visible .mark { stroke: #fff; }
    .tooltip { position: absolute; z-index: 2; display: grid; gap: 2px; min-width: 150px; padding: .5rem .65rem; pointer-events: none;
      transform: translate(-50%, calc(-100% - 14px)); background: #1b1411; border: 1px solid #729661; border-radius: 8px;
      box-shadow: 0 6px 18px rgba(0,0,0,.45); font-size: .82rem; color: var(--color-text-muted); white-space: nowrap; }
    .tooltip.flip { transform: translate(calc(-100% + 18px), calc(-100% - 14px)); }
    .tooltip.start { transform: translate(-18px, calc(-100% - 14px)); }
    .tooltip.below { transform: translate(-50%, 14px); } .tooltip.below.flip { transform: translate(calc(-100% + 18px), 14px); }
    .tooltip.below.start { transform: translate(-18px, 14px); }
    .tooltip strong { color: #d9efbf; font-size: .95rem; }
    .legend { display: flex; flex-wrap: wrap; align-items: center; gap: .35rem .5rem; margin: 0; color: var(--color-text-muted); font-size: .82rem; }
    .key { display: inline-block; width: 10px; height: 10px; border-radius: 50%; margin-left: .4rem; }
    .key.sale { background: #b4ce93; } .key.auction { background: #e0c27a; } .key.offer { border: 2px solid #8fb3d9; }
    .muted { margin: 0; color: var(--color-text-muted); }
  `]
})
export class PriceChartComponent {
  readonly entries = input.required<PriceHistoryEntry[]>();
  readonly upgradeable = input(true);

  protected readonly height = HEIGHT;
  protected readonly width = signal(720);
  protected readonly level = signal<number | null>(null);
  protected readonly active = signal<ChartPoint | null>(null);

  protected readonly levels = computed(() => [...new Set(this.entries().map(e => e.enhancement))].sort((a, b) => a - b));

  readonly chart = computed<Chart | null>(() => {
    const level = this.upgradeable() ? this.level() : null;
    const data = this.entries()
      .filter(e => e.plnGrosze !== null && e.plnGrosze > 0 && (level === null || e.enhancement === level))
      .map(entry => ({ entry, time: Date.parse(entry.at) }))
      .sort((a, b) => a.time - b.time);
    if (!data.length) return null;

    let from = data[0].time, to = data[data.length - 1].time;
    if (to - from < DAY) { from -= DAY / 2; to += DAY / 2; }
    const highest = Math.max(...data.map(d => d.entry.plnGrosze!));
    const step = niceStep(highest / 4);
    const yMax = Math.max(step, Math.ceil(highest * 1.05 / step) * step);
    const left = PAD.left, right = Math.max(left + 40, this.width() - PAD.right), top = PAD.top, bottom = HEIGHT - PAD.bottom;
    const x = (time: number) => left + (time - from) / (to - from) * (right - left);
    const y = (grosze: number) => bottom - grosze / yMax * (bottom - top);

    const points = data.map(d => ({ entry: d.entry, x: x(d.time), y: y(d.entry.plnGrosze!) }));
    const path = points.map((p, i) => i === 0 ? `M${p.x},${p.y}` : `H${p.x}V${p.y}`).join('') + `H${right}`;
    const yTicks = Array.from({ length: Math.round(yMax / step) + 1 }, (_, i) => ({ y: y(step * i), label: formatPln(step * i) }));
    const count = Math.max(2, Math.min(6, Math.floor((right - left) / 110)));
    const xTicks = Array.from({ length: count }, (_, i) => {
      const time = from + (to - from) * i / (count - 1);
      return { x: x(time), at: new Date(time) };
    });
    return { points, path, yTicks, xTicks, left, right, top, bottom };
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

  protected pln(grosze: number): string { return formatPln(grosze); }
  protected amount(entry: PriceHistoryEntry): string {
    return entry.currency === 'GOLD' ? formatGold(entry.amount) : formatPln(entry.amount);
  }
  protected source(entry: PriceHistoryEntry): string {
    return entry.source === 'SALE' ? 'Sprzedaż' : entry.source === 'AUCTION' ? 'Licytacja' : 'Oferta';
  }
  protected describe(entry: PriceHistoryEntry): string {
    const date = new Date(entry.at).toLocaleDateString('pl-PL');
    return `${this.source(entry)} ${date}, ${entry.serverName}, +${entry.enhancement}: ${this.amount(entry)}`;
  }
}

/** Grid step in grosze: 1, 2 or 5 times a power of ten, at least 1 zł. */
export function niceStep(grosze: number): number {
  const raw = Math.max(100, grosze);
  const power = 10 ** Math.floor(Math.log10(raw));
  const factor = raw / power;
  return (factor <= 1 ? 1 : factor <= 2 ? 2 : factor <= 5 ? 5 : 10) * power;
}
