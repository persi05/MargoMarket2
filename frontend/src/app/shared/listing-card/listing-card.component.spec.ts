import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject, of } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { GoldListingResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { ListingService } from '../../core/services/listing.service';
import { ListingCardComponent } from './listing-card.component';

const gold: GoldListingResponse = {
  id: 5, itemId: null, itemName: 'Złoto', iconUrl: null, itemDescription: null, itemStats: null, itemType: null, level: null,
  enhancementLevel: 0, bound: false, rarity: null, price: null, currency: { id: 1, name: 'pln' }, server: { id: 2, name: 'Classic' },
  contact: null, status: 'active', listingKind: 'GOLD', goldAmount: 1_500_000_000, priceMinor: 3_450, goldRateMode: 'PLN_PER_G',
  rateGoldUnits: 1_000_000_000, ratePriceMinor: 2_300, description: 'Szybka wymiana', sellerId: 1,
  sellerDisplayName: 'Gracz', createdAt: '2026-11-20T10:00:00', soldAt: null
};

describe('listing card for gold', () => {
  afterEach(() => TestBed.resetTestingModule());

  function render(inputs: Record<string, unknown>) {
    const listings = { withdraw: vi.fn(() => of({ ...gold, status: 'withdrawn' })) };
    TestBed.configureTestingModule({
      imports: [ListingCardComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: { user$: new BehaviorSubject({ id: 1 }) } },
        { provide: ListingService, useValue: listings }]
    });
    const fixture = TestBed.createComponent(ListingCardComponent);
    for (const [name, value] of Object.entries({ listing: gold, ...inputs })) fixture.componentRef.setInput(name, value);
    fixture.detectChanges();
    return { fixture, element: fixture.nativeElement as HTMLElement };
  }

  it('uses the item card layout with a gold icon, rate and world', () => {
    const { element } = render({ showOwnerActions: true });
    expect(element.querySelector('.listing-head .gold-frame img')?.getAttribute('src')).toBe('icons/gold-coins.svg');
    expect(element.querySelector('h3')?.textContent).toContain('1,5g');
    expect(element.querySelector('.meta')?.textContent).toContain('Świat: Classic');
    expect(element.textContent).toContain('Szybka wymiana');
  });

  it('gives the owner edit, sold, withdraw and delete actions', () => {
    const { element } = render({ showOwnerActions: true });
    const actions = Array.from(element.querySelectorAll('.actions a, .actions button')).map(e => e.textContent?.trim());
    expect(actions).toEqual(['Edytuj', 'Oznacz jako sprzedane', 'Zakończ bez sprzedaży', 'Usuń']);
  });
});
