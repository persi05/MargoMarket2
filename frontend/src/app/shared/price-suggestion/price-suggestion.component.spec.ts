import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { PriceLevel, PriceOverview } from '../../core/models/api.models';
import { PriceService } from '../../core/services/price.service';
import { PriceSuggestionComponent, roundGold } from './price-suggestion.component';

function level(enhancement: number, changes: Partial<PriceLevel> = {}): PriceLevel {
  return { enhancement, basis: 'ESTIMATED', baseGrosze: 11_000, upgradeGrosze: 7_480, totalGrosze: 18_480, totalGold: 3_696_000_000,
    sampleCount: 4, lowData: false, upgradeRateMissing: false, ...changes };
}

const overview: PriceOverview = {
  itemId: 7, itemName: 'Legenda', rarity: 'Legendarny', level: 40, serverId: 3, serverName: 'Classic', worldType: 'TRANSFERABLE',
  goldPerPln: 20_000_000, recentOnly: true, recentDays: 180, marketWorlds: ['Classic', 'Gefion'],
  levels: [
    level(0, { basis: 'DIRECT', baseGrosze: null, upgradeGrosze: 0, totalGrosze: 11_000, totalGold: 2_200_000_000 }),
    level(1), level(2), level(3),
    level(4, { totalGrosze: null, totalGold: null, upgradeGrosze: null, upgradeRateMissing: true }),
    level(5, { basis: 'NONE', baseGrosze: null, totalGrosze: null, totalGold: null, sampleCount: 0, lowData: true })
  ]
};

describe('price suggestion', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render(inputs: Record<string, unknown>, response = of(overview)) {
    const service = { overview: vi.fn(() => response) };
    TestBed.configureTestingModule({ imports: [PriceSuggestionComponent], providers: [provideRouter([]), { provide: PriceService, useValue: service }] });
    const fixture = TestBed.createComponent(PriceSuggestionComponent);
    for (const [name, value] of Object.entries({ itemId: 7, serverId: 3, ...inputs })) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, service, text: () => fixture.nativeElement.textContent as string };
  }

  it('shows the estimated price with its breakdown and market', async () => {
    const { text, service } = await render({ enhancement: 3 });
    expect(service.overview).toHaveBeenCalledWith(7, 3, false);
    expect(text()).toContain('Sugerowana cena (+3):');
    expect(text()).toContain('185 PLN');
    expect(text()).toContain('≈ 3,7g');
    expect(text()).toContain('Cena +0: 110 PLN · ulepszenie do +3: 75 PLN');
    expect(text()).toContain('Na podstawie 4 transakcji · rynek: światy z przenosinami');
  });

  it('emits the price when the seller uses it', async () => {
    const { fixture } = await render({ enhancement: 0, useable: true });
    const used = vi.fn();
    fixture.componentInstance.use.subscribe(used);
    (fixture.nativeElement.querySelector('button') as HTMLButtonElement).click();
    expect(used).toHaveBeenCalledWith({ grosze: 11_000, gold: 2_200_000_000 });
  });

  it('explains a missing upgrade rate and a missing history', async () => {
    const missingRate = await render({ enhancement: 4 });
    expect(missingRate.text()).toContain('Cena +0: 110 PLN');
    expect(missingRate.text()).toContain('Brak przelicznika ulepy dla świata Classic');
    expect(missingRate.fixture.nativeElement.querySelector('button')).toBeNull();
    TestBed.resetTestingModule();

    const noHistory = await render({ enhancement: 5 });
    expect(noHistory.text()).toContain('Brak historii cen tego przedmiotu na tym rynku.');
  });

  it('does not ask without an item or world and survives errors', async () => {
    const idle = await render({ itemId: null });
    expect(idle.service.overview).not.toHaveBeenCalled();
    TestBed.resetTestingModule();

    const failed = await render({}, throwError(() => new Error('boom')));
    expect(failed.text()).toContain('Nie udało się pobrać sugerowanej ceny.');
  });

  it('rounds gold to readable steps', () => {
    expect(roundGold(3_696_000_000)).toBe(3_700_000_000);
    expect(roundGold(123_456_789)).toBe(123_000_000);
    expect(roundGold(10)).toBe(1_000_000);
  });
});
