import { Component, computed, inject, input, output, signal } from '@angular/core';
import { takeUntilDestroyed, toObservable } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { catchError, map, of, startWith, switchMap } from 'rxjs';

import { PriceLevel, PriceOverview } from '../../core/models/api.models';
import { PriceService } from '../../core/services/price.service';
import { formatGold, formatPln } from '../../core/utils/gold-format';

export interface SuggestedPrice { grosze: number; gold: number | null; }

type State = { status: 'idle' | 'loading' | 'error' } | { status: 'ready'; overview: PriceOverview };

/** Suggested price of one catalog item on one world, with how it was calculated. */
@Component({
  selector: 'mm-price-suggestion', standalone: true, imports: [RouterLink],
  template: `
    @switch (state().status) {
      @case ('loading') { <p class="price-suggestion muted" role="status">Liczę sugerowaną cenę…</p> }
      @case ('error') { <p class="price-suggestion muted">Nie udało się pobrać sugerowanej ceny.</p> }
    }
    @if (overview(); as data) {
      @if (level(); as row) {
        <section class="price-suggestion" [class.compact]="compact()" aria-label="Sugerowana cena">
          @if (row.totalGrosze !== null) {
            <p class="headline">Sugerowana cena{{ effectiveEnhancement() > 0 ? ' (+' + effectiveEnhancement() + ')' : '' }}:
              <strong>{{ pln(row.totalGrosze) }}</strong>
              @if (row.totalGold !== null) { <span class="gold">≈ {{ gold(row.totalGold) }}</span> }
            </p>
          } @else if (row.baseGrosze !== null) {
            <p class="headline">Cena +0: <strong>{{ pln(row.baseGrosze) }}</strong></p>
          } @else {
            <p class="headline muted">Brak historii cen tego przedmiotu na tym rynku.</p>
          }
          @if (!compact()) {
            @if (row.basis === 'ESTIMATED' && row.totalGrosze !== null && effectiveEnhancement() > 0) {
              <p class="breakdown">Cena +0: {{ pln(row.baseGrosze!) }} · ulepszenie do +{{ effectiveEnhancement() }}: {{ pln(row.upgradeGrosze!) }}</p>
            }
            @if (row.basis === 'DIRECT' && effectiveEnhancement() > 0) {
              <p class="breakdown">Wyliczona z transakcji przedmiotów na +{{ effectiveEnhancement() }}.</p>
            }
            @if (row.upgradeRateMissing && effectiveEnhancement() > 0) {
              <p class="warning">Brak przelicznika ulepy dla świata {{ data.serverName }} – nie da się doliczyć ulepszenia.</p>
            }
          }
          @if (row.sampleCount > 0) {
            <p class="meta">Na podstawie {{ row.sampleCount }} transakcji · rynek: {{ market(data) }}{{ data.recentOnly ? '' : ' · także starsze niż ' + data.recentDays + ' dni' }}</p>
          }
          @if (row.lowData && row.sampleCount > 0) { <p class="warning">Mało danych – traktuj cenę orientacyjnie.</p> }
          <div class="actions">
            @if (useable() && row.totalGrosze !== null) {
              <button type="button" (click)="use.emit({ grosze: row.totalGrosze, gold: row.totalGold })">Użyj tej ceny</button>
            }
            <a [routerLink]="['/prices']" [queryParams]="{ item: data.itemId, world: data.serverId }">Historia cen</a>
          </div>
        </section>
      }
    }
  `,
  styles: [`
    .price-suggestion { display: grid; gap: .3rem; margin: 0; padding: .8rem 1rem; border: 1px solid rgba(168,201,138,.3); border-radius: 8px; background: rgba(49,84,43,.18); color: #f1ede4; }
    .price-suggestion.compact { padding: .5rem .6rem; font-size: .9rem; }
    p { margin: 0; line-height: 1.45; }
    .headline strong { color: #d9efbf; font-size: 1.05rem; } .gold { color: #e0c27a; margin-left: .35rem; }
    .breakdown, .meta, .muted { color: #b9ad9f; font-size: .85rem; } .warning { color: #f0c27a; font-size: .85rem; }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: .75rem; margin-top: .2rem; }
    button { padding: .4rem .8rem; background: #31542b; color: #f1ede4; border: 1px solid #729661; border-radius: 6px; font: inherit; font-weight: 700; cursor: pointer; }
    a { color: #b4ce93; } button:focus-visible, a:focus-visible { outline: 2px solid #b4ce93; outline-offset: 2px; }
  `]
})
export class PriceSuggestionComponent {
  private readonly prices = inject(PriceService);

  readonly itemId = input<number | null>(null);
  readonly serverId = input<number | null>(null);
  readonly enhancement = input(0);
  readonly bound = input(false);
  readonly compact = input(false);
  readonly useable = input(false);
  readonly use = output<SuggestedPrice>();

  protected readonly state = signal<State>({ status: 'idle' });
  protected readonly overview = computed(() => { const s = this.state(); return s.status === 'ready' ? s.overview : null; });
  /** Items that cannot be upgraded only have a +0 price, whatever enhancement the form holds. */
  protected readonly effectiveEnhancement = computed(() => this.overview()?.upgradeable === false ? 0 : this.enhancement());
  protected readonly level = computed<PriceLevel | null>(() =>
    this.overview()?.levels.find(l => l.enhancement === this.effectiveEnhancement()) ?? null);

  constructor() {
    const query = computed(() => ({ itemId: this.itemId(), serverId: this.serverId(), bound: this.bound() }),
      { equal: (a, b) => a.itemId === b.itemId && a.serverId === b.serverId && a.bound === b.bound });
    toObservable(query).pipe(
      switchMap(({ itemId, serverId, bound }) => !itemId || !serverId ? of<State>({ status: 'idle' })
        : this.prices.overview(itemId, serverId, bound).pipe(
          map(overview => ({ status: 'ready', overview }) as State),
          catchError(() => of<State>({ status: 'error' })),
          startWith<State>({ status: 'loading' }))),
      takeUntilDestroyed()
    ).subscribe(state => this.state.set(state));
  }

  protected pln(grosze: number): string { return formatPln(Math.round(grosze / 100) * 100); }
  protected gold(units: number): string { return formatGold(roundGold(units)); }
  protected market(data: PriceOverview): string {
    return data.worldType === 'TRANSFERABLE' ? 'światy z przenosinami' : data.serverName;
  }
}

/** Rounds gold to a readable precision: whole millions, or 0,1g above 1g. */
export function roundGold(units: number): number {
  const step = units >= 1_000_000_000 ? 100_000_000 : 1_000_000;
  return Math.max(step, Math.round(units / step) * step);
}
