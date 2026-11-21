import { ItemPreviewComponent } from '../../shared/item-preview/item-preview.component';
import { MarketLayoutComponent } from '../../shared/market-layout/market-layout.component';
import { MarketFilterComponent } from '../../shared/market-layout/market-filter.component';
import { AsyncPipe } from '@angular/common';
import { DestroyRef, Component, HostListener, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, catchError, combineLatest, debounceTime, distinctUntilChanged, finalize, of, shareReplay, switchMap, tap } from 'rxjs';

import { ListingResponse, LookupResponse, PageResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { formatListingPrice } from '../../core/utils/price-format';
import { hasEnhancementLevel, marketItemTypes } from '../../core/utils/item-types';
import { PrivateDetailComponent } from '../../shared/private-detail/private-detail.component';
import { DiscussionDrawerComponent } from './discussion-drawer.component';

@Component({
  selector: 'mm-market-page',
  standalone: true,
  imports: [ItemPreviewComponent, MarketLayoutComponent, MarketFilterComponent, AsyncPipe, ReactiveFormsModule, RouterLink, DiscussionDrawerComponent, PrivateDetailComponent],
  templateUrl: './market-page.component.html',
  styleUrl: './market-page.component.css'
})
export class MarketPageComponent {

  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  protected readonly authService = inject(AuthService);
  protected readonly dictionaries$ = inject(DictionaryService).getAll();
  private readonly pageSubject = new BehaviorSubject(1);
  private lastLoaded = 0;
  private backgroundRefresh = false;
  private requestInFlight = false;

  protected readonly filters = this.fb.nonNullable.group({
    search: [''],
    serverId: [''],
    itemTypeId: [''],
    bound: [''],
    currencyId: [''],
    minLevel: [''],
    maxLevel: ['']
  });

  protected loading = false;
  protected loadError = '';
  protected notice = '';
  protected readonly skeletonRows = [1, 2, 3, 4, 5, 6];
  protected readonly favoriteIds = new Set<number>();
  private favoriteRequestVersion = 0;
  protected readonly favoriteBusyIds = new Set<number>();
  protected activeDiscussionListing: ListingResponse | null = null;
  protected readonly listings$ = combineLatest([this.pageSubject]).pipe(
    switchMap(([page]) => {
      this.listingService.marketState['ITEM']={filter:{...this.filters.getRawValue()},page};
      this.loading = !this.backgroundRefresh;
      this.backgroundRefresh = false;
      this.requestInFlight = true;
      this.loadError = '';
      this.notice = '';
      return this.listingService.search({ ...this.filters.getRawValue(), page }).pipe(
        catchError(() => {
          this.loadError = 'Nie udało się pobrać ogłoszeń. Spróbuj ponownie.';
          return of(null);
        }),
        tap(result => {
          if (result) {
            this.lastLoaded = Date.now();
            this.reloadFavorites(result.content.map(listing => listing.id));
          }
        }),
        finalize(() => {
          this.loading = false;
          this.requestInFlight = false;
        })
      );
    }),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  constructor() {
    const state=this.listingService.marketState['ITEM'];
    if(state){this.filters.patchValue(state.filter);this.pageSubject.next(state.page);}
    const timer = setInterval(() => this.refreshVisible(), 60000);
    const authSub = this.authService.user$.subscribe(() => {
      this.activeDiscussionListing = null;
      this.backgroundRefresh = false;
      this.pageSubject.next(this.pageSubject.value);
    });
    inject(DestroyRef).onDestroy(()=>{clearInterval(timer);authSub.unsubscribe()});

    this.filters.controls.search.valueChanges.pipe(
      debounceTime(250),
      distinctUntilChanged()
    ).subscribe(() => {
      this.pageSubject.next(1);
    });

  }

  @HostListener('document:visibilitychange')
  protected refreshVisible(): void {
    if (document.hidden || this.requestInFlight || Date.now() - this.lastLoaded < 30000) return;
    this.backgroundRefresh = true;
    this.pageSubject.next(this.pageSubject.value);
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
        } else {
          this.favoriteIds.add(listing.id);
        }
      },
      error: () => {
        this.notice = shouldRemove
          ? 'Nie udało się usunąć ogłoszenia z obserwowanych.'
          : 'Nie udało się dodać ogłoszenia do obserwowanych.';
      }
    });
  }

  protected openDiscussion(listing: ListingResponse): void {
    this.activeDiscussionListing = listing;
  }

  protected closeDiscussion(): void {
    this.activeDiscussionListing = null;
  }

  protected priceLabel(listing: ListingResponse): string {
    return formatListingPrice(listing.price, listing.currency.name);
  }

  protected currencyLabel(name: string): string {
    return name.trim().toLowerCase() === 'pln' ? 'zł' : name;
  }

  protected itemTypes(types: LookupResponse[]): LookupResponse[] {
    return marketItemTypes(types);
  }

  protected enhancementLabel(listing: ListingResponse): string {
    return hasEnhancementLevel((listing.itemType?.name ?? ''), listing.itemName) ? `+${listing.enhancementLevel}` : '-';
  }

  private reloadFavorites(listingIds: number[]): void {
    const requestVersion = ++this.favoriteRequestVersion;
    if (!this.authService.isLoggedIn) {
      this.favoriteIds.clear();
      return;
    }

    if (!listingIds.length) {
      this.favoriteIds.clear();
      return;
    }

    this.listingService.favoriteIds(listingIds).subscribe({
      next: (ids) => {
        if (requestVersion !== this.favoriteRequestVersion) return;
        this.favoriteIds.clear();
        ids.forEach((id) => this.favoriteIds.add(id));
      }
    });
  }

}
