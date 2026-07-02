import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, debounceTime, distinctUntilChanged, finalize, shareReplay, switchMap, tap } from 'rxjs';

import { ListingResponse, PageResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { formatListingPrice } from '../../core/utils/price-format';
import { itemStatLines } from '../../core/utils/item-stats';

@Component({
  selector: 'mm-market-page',
  standalone: true,
  imports: [AsyncPipe, ReactiveFormsModule],
  templateUrl: './market-page.component.html',
  styleUrl: './market-page.component.css'
})
export class MarketPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  protected readonly authService = inject(AuthService);
  protected readonly dictionaries$ = inject(DictionaryService).getAll();
  private readonly pageSubject = new BehaviorSubject(1);

  protected readonly filters = this.fb.nonNullable.group({
    search: [''],
    serverId: [''],
    itemTypeId: [''],
    rarityId: [''],
    currencyId: [''],
    minLevel: [''],
    maxLevel: ['']
  });

  protected loading = false;
  protected notice = '';
  protected readonly favoriteIds = new Set<number>();
  protected readonly favoriteBusyIds = new Set<number>();
  protected readonly brokenListingImageIds = new Set<number>();

  protected readonly listings$ = combineLatest([this.pageSubject]).pipe(
    tap(() => {
      this.loading = true;
      this.notice = '';
    }),
    switchMap(([page]) => this.listingService.search({ ...this.filters.getRawValue(), page }).pipe(
      finalize(() => {
        this.loading = false;
      })
    )),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  constructor() {
    this.filters.controls.search.valueChanges.pipe(
      debounceTime(250),
      distinctUntilChanged()
    ).subscribe(() => {
      this.pageSubject.next(1);
    });

    this.reloadFavorites();
  }

  applyFilters(): void {
    this.pageSubject.next(1);
  }

  resetFilters(): void {
    this.filters.reset();
    this.applyFilters();
  }

  nextPage(page: PageResponse<ListingResponse>): void {
    if (!page.last) {
      this.pageSubject.next(page.page + 1);
    }
  }

  previousPage(page: PageResponse<ListingResponse>): void {
    if (!page.first) {
      this.pageSubject.next(page.page - 1);
    }
  }

  isFavorite(listing: ListingResponse): boolean {
    return this.favoriteIds.has(listing.id);
  }

  isFavoriteBusy(listing: ListingResponse): boolean {
    return this.favoriteBusyIds.has(listing.id);
  }

  toggleFavorite(listing: ListingResponse): void {
    if (!this.authService.isLoggedIn || this.favoriteBusyIds.has(listing.id)) {
      return;
    }

    this.favoriteBusyIds.add(listing.id);
    this.notice = '';
    const shouldRemove = this.favoriteIds.has(listing.id);
    const request$ = shouldRemove
      ? this.listingService.removeFavorite(listing.id)
      : this.listingService.addFavorite(listing.id);

    request$.pipe(finalize(() => {
      this.favoriteBusyIds.delete(listing.id);
    })).subscribe({
      next: () => {
        if (shouldRemove) {
          this.favoriteIds.delete(listing.id);
          this.notice = `${listing.itemName} usunięto z obserwowanych.`;
        } else {
          this.favoriteIds.add(listing.id);
          this.notice = `${listing.itemName} dodano do obserwowanych.`;
        }
      },
      error: () => {
        this.notice = shouldRemove
          ? 'Nie udało się usunąć ogłoszenia z obserwowanych.'
          : 'Nie udało się dodać ogłoszenia do obserwowanych.';
      }
    });
  }

  protected priceLabel(listing: ListingResponse): string {
    return formatListingPrice(listing.price, listing.currency.name);
  }

  protected rarityClass(listing: ListingResponse): string {
    const rarity = this.normalizeRarity(listing.rarity.name);

    if (rarity.includes('legendarn')) {
      return 'rarity-legendary';
    }

    if (rarity.includes('heroiczn')) {
      return 'rarity-heroic';
    }

    if (rarity.includes('unikat')) {
      return 'rarity-unique';
    }

    if (rarity.includes('ulepszon')) {
      return 'rarity-upgraded';
    }

    return 'rarity-normal';
  }

  protected currencyLabel(name: string): string {
    return name.trim().toLowerCase() === 'pln' ? 'PLN' : name;
  }

  protected showListingImage(listing: ListingResponse): boolean {
    return Boolean(listing.iconUrl) && !this.brokenListingImageIds.has(listing.id);
  }

  protected markListingImageBroken(listing: ListingResponse): void {
    this.brokenListingImageIds.add(listing.id);
  }

  protected statLines(listing: ListingResponse) {
    return itemStatLines(listing.itemStats);
  }

  private reloadFavorites(): void {
    if (!this.authService.isLoggedIn) {
      this.favoriteIds.clear();
      return;
    }

    this.listingService.favorites().subscribe({
      next: (listings) => {
        this.favoriteIds.clear();
        listings.forEach((listing) => this.favoriteIds.add(listing.id));
      }
    });
  }

  private normalizeRarity(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  }
}
