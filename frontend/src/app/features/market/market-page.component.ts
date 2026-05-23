import { AsyncPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, finalize, shareReplay, switchMap, tap } from 'rxjs';

import { ListingResponse, PageResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';

@Component({
  selector: 'mm-market-page',
  standalone: true,
  imports: [AsyncPipe, ReactiveFormsModule, ListingCardComponent],
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
}
