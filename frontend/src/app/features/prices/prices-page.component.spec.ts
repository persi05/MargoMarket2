import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PageResponse, PriceHistoryEntry, PriceOverview } from '../../core/models/api.models';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ItemService } from '../../core/services/item.service';
import { PriceService } from '../../core/services/price.service';
import { PricesPageComponent } from './prices-page.component';

const overview: PriceOverview = {
  itemId: 7, itemName: 'Legenda testowa', rarity: 'Legendarny', level: 40, serverId: 2, serverName: 'Classic', worldType: 'TRANSFERABLE',
  goldPerPln: 20_000_000, recentOnly: true, recentDays: 180, marketWorlds: ['Classic', 'Gefion'],
  levels: [0, 1, 2, 3, 4, 5].map(n => ({
    enhancement: n, basis: n === 0 ? 'DIRECT' : 'ESTIMATED', baseGrosze: n === 0 ? null : 11_000, upgradeGrosze: n * 1_000,
    totalGrosze: 11_000 + n * 1_000, totalGold: (11_000 + n * 1_000) * 200_000, sampleCount: 4, lowData: false, upgradeRateMissing: false
  }) as const)
};

const history: PageResponse<PriceHistoryEntry> = {
  content: [
    { listingId: 1, source: 'AUCTION', serverName: 'Classic', enhancement: 3, bound: false, currency: 'PLN', amount: 18_500, plnGrosze: 18_500, at: '2026-11-01T10:00:00Z' },
    { listingId: 2, source: 'SALE', serverName: 'Gefion', enhancement: 0, bound: true, currency: 'GOLD', amount: 2_500_000_000, plnGrosze: 10_000, at: '2026-10-30T10:00:00Z' }
  ],
  page: 1, size: 20, totalElements: 2, totalPages: 1, first: true, last: true
};

describe('prices page', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render(query: Record<string, string>) {
    const prices = { overview: vi.fn(() => of(overview)), history: vi.fn(() => of(history)) };
    const items = { search: vi.fn(() => of([{ id: 9, name: 'Inna legenda', iconUrl: '', level: 50, rarity: { id: 4, name: 'Legendarny' } }])) };
    TestBed.configureTestingModule({
      imports: [PricesPageComponent],
      providers: [
        provideRouter([]),
        { provide: ActivatedRoute, useValue: { snapshot: { queryParamMap: convertToParamMap(query) } } },
        { provide: PriceService, useValue: prices },
        { provide: ItemService, useValue: items },
        { provide: DictionaryService, useValue: { getAll: () => of({ servers: [{ id: 1, name: 'Aether' }, { id: 2, name: 'Classic' }], currencies: [], rarities: [], itemTypes: [] }) } }
      ]
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(PricesPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, prices, items, text: () => fixture.nativeElement.textContent as string };
  }

  it('loads prices and history for the item and world from the link', async () => {
    const { prices, text } = await render({ item: '7', world: '2' });
    expect(prices.overview).toHaveBeenCalledWith(7, 2, false);
    expect(prices.history).toHaveBeenCalledWith(7, 2, 1);
    expect(text()).toContain('Legenda testowa');
    expect(text()).toContain('Światy z przenosinami (Classic, Gefion)');
    expect(text()).toContain('140 PLN');
    expect(text()).toContain('z transakcji na +0');
    expect(text()).toContain('Licytacja');
    expect(text()).toContain('2,5g');
    expect(text()).toContain('związany');
  });

  it('asks to search when no item is chosen and loads a found item', async () => {
    const { fixture, prices, items, text } = await render({});
    expect(text()).toContain('Wyszukaj przedmiot');
    expect(prices.overview).not.toHaveBeenCalled();

    vi.useFakeTimers();
    const page = fixture.componentInstance as any;
    page.searchChanged('legen');
    vi.advanceTimersByTime(250);
    vi.useRealTimers();
    expect(items.search).toHaveBeenCalledWith({ search: 'legen', limit: 20 });

    page.choose(page.results()[0]);
    expect(prices.overview).toHaveBeenCalledWith(9, 1, false);
  });
});
