import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, distinctUntilChanged, of, switchMap } from 'rxjs';

import { ItemResponse, LookupResponse, PageResponse, PriceHistoryEntry, PriceLevel, PriceOverview } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ItemService } from '../../core/services/item.service';
import { PriceService } from '../../core/services/price.service';
import { formatGold, formatPln } from '../../core/utils/gold-format';
import { PriceChartComponent } from './price-chart.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { roundGold } from '../../shared/price-suggestion/price-suggestion.component';

/** Newest transactions drawn on the chart (backend allows up to 500 per page). */
const CHART_SIZE = 500;

interface SelectedItem { id: number; name: string; }

@Component({
  selector: 'mm-prices-page', standalone: true, imports: [PageHeaderComponent, FormsModule, RouterLink, DatePipe, PaginationComponent, PriceChartComponent],
  templateUrl: './prices-page.component.html', styleUrl: './prices-page.component.css'
})
export class PricesPageComponent {
  private readonly prices = inject(PriceService);
  private readonly items = inject(ItemService);
  private readonly router = inject(Router);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly searchTerms = new Subject<string>();

  /** Guests see the page but prices need an account. */
  protected readonly loggedIn = inject(AuthService).isLoggedIn;
  protected search = '';
  protected readonly results = signal<ItemResponse[]>([]);
  protected readonly servers = signal<LookupResponse[]>([]);
  protected readonly item = signal<SelectedItem | null>(null);
  protected serverId: number | null = null;
  protected bound = false;
  protected readonly overview = signal<PriceOverview | null>(null);
  protected readonly history = signal<PageResponse<PriceHistoryEntry> | null>(null);
  /** Whole recent history for the chart; the table below pages through the same data. */
  protected readonly chartEntries = signal<PriceHistoryEntry[]>([]);
  protected readonly loading = signal(false);
  protected readonly error = signal('');

  constructor() {
    const params = this.route.snapshot.queryParamMap;
    const itemId = Number(params.get('item')) || null;
    this.serverId = Number(params.get('world')) || null;
    if (itemId) this.item.set({ id: itemId, name: '' });

    inject(DictionaryService).getAll().pipe(takeUntilDestroyed()).subscribe(dictionaries => {
      this.servers.set(dictionaries.servers);
      if (!this.serverId && dictionaries.servers.length) this.serverId = dictionaries.servers[0].id;
      this.load();
    });

    this.searchTerms.pipe(
      debounceTime(200), distinctUntilChanged(),
      switchMap(term => term.trim().length < 2 ? of([]) : this.items.search({ search: term, limit: 20 }).pipe(catchError(() => of([])))),
      takeUntilDestroyed()
    ).subscribe(found => this.results.set(found));
  }

  protected searchChanged(term: string): void {
    this.searchTerms.next(term);
  }

  protected choose(found: ItemResponse): void {
    this.item.set({ id: found.id, name: found.name });
    this.search = '';
    this.results.set([]);
    this.load();
  }

  protected load(page = 1): void {
    const item = this.item();
    if (!item || !this.serverId || !this.loggedIn) return;
    void this.router.navigate([], { relativeTo: this.route, queryParams: { item: item.id, world: this.serverId }, replaceUrl: true });
    this.loading.set(true);
    this.error.set('');
    this.prices.overview(item.id, this.serverId, this.bound).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: overview => {
        if (!overview.bindable) this.bound = false;
        this.overview.set(overview);
        this.item.set({ id: overview.itemId, name: overview.itemName });
        this.loading.set(false);
      },
      error: () => {
        this.overview.set(null);
        this.loading.set(false);
        this.error.set('Nie udało się pobrać cen tego przedmiotu.');
      }
    });
    this.loadHistory(page);
    this.prices.history(item.id, this.serverId, 1, CHART_SIZE).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: history => this.chartEntries.set(history.content),
      error: () => this.chartEntries.set([])
    });
  }

  protected loadHistory(page: number): void {
    const item = this.item();
    if (!item || !this.serverId || !this.loggedIn) return;
    this.prices.history(item.id, this.serverId, page).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: history => this.history.set(history),
      error: () => this.history.set(null)
    });
  }

  protected scales(count: number): string { return count.toLocaleString('pl-PL'); }
  protected pln(grosze: number | null): string { return grosze === null ? '—' : formatPln(Math.round(grosze / 100) * 100); }
  protected gold(units: number | null): string { return units === null ? '—' : formatGold(roundGold(units)); }
  protected basis(level: PriceLevel, upgradeable = true): string {
    if (level.basis === 'DIRECT') return upgradeable ? 'z transakcji na +' + level.enhancement : 'z transakcji';
    if (level.basis === 'ESTIMATED') return level.upgradeRateMissing ? 'brak przelicznika ulepy' : 'cena +0 + ulepa';
    return 'brak danych';
  }
  protected source(entry: PriceHistoryEntry): string {
    return entry.source === 'SALE' ? 'Sprzedaż' : entry.source === 'AUCTION' ? 'Licytacja' : 'Oferta';
  }
  protected amount(entry: PriceHistoryEntry): string {
    return entry.currency === 'GOLD' ? formatGold(entry.amount) : formatPln(entry.amount);
  }
  protected market(overview: PriceOverview): string {
    return overview.worldType === 'TRANSFERABLE'
      ? 'Światy z przenosinami (' + overview.marketWorlds.join(', ') + ')'
      : 'Tylko ' + overview.serverName;
  }
}
