import { AsyncPipe } from '@angular/common';
import { Component, ElementRef, HostListener, QueryList, ViewChild, ViewChildren, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, combineLatest, debounceTime, distinctUntilChanged, finalize, shareReplay, switchMap, tap } from 'rxjs';

import { ListingResponse, LookupResponse, PageResponse } from '../../core/models/api.models';
import { ItemDescriptionPipe } from '../../core/pipes/item-description.pipe';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { formatListingPrice } from '../../core/utils/price-format';
import { itemLastAvailableDuring, itemRequiredProfessions, itemStatLines } from '../../core/utils/item-stats';
import { hasEnhancementLevel, marketItemTypes } from '../../core/utils/item-types';
import { DiscussionDrawerComponent } from './discussion-drawer.component';

@Component({
  selector: 'mm-market-page',
  standalone: true,
  imports: [AsyncPipe, ReactiveFormsModule, RouterLink, ItemDescriptionPipe, DiscussionDrawerComponent],
  templateUrl: './market-page.component.html',
  styleUrl: './market-page.component.css'
})
export class MarketPageComponent {
  @ViewChild('filterPanel') private filterPanel?: ElementRef<HTMLElement>;
  @ViewChildren('pinnedPreview') private pinnedPreviewElements?: QueryList<ElementRef<HTMLElement>>;

  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  protected readonly authService = inject(AuthService);
  protected readonly dictionaries$ = inject(DictionaryService).getAll();
  private readonly pageSubject = new BehaviorSubject(1);

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
  protected notice = '';
  protected readonly favoriteIds = new Set<number>();
  protected readonly favoriteBusyIds = new Set<number>();
  protected readonly brokenListingImageIds = new Set<number>();
  protected activeDiscussionListing: ListingResponse | null = null;
  protected filterPosition: { x: number; y: number } | null = null;
  protected filterDragging = false;
  protected pinnedListings: ListingResponse[] = [];
  protected readonly previewPositions = new Map<number, { x: number; y: number }>();
  protected previewDraggingId: number | null = null;
  protected frontPreviewId: number | null = null;
  private previewDragState: {
    listingId: number;
    pointerId: number;
    offsetX: number;
    offsetY: number;
    width: number;
    height: number;
  } | null = null;
  private filterDragState: {
    pointerId: number;
    offsetX: number;
    offsetY: number;
    width: number;
    height: number;
  } | null = null;

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

  protected startFilterDrag(event: PointerEvent): void {
    if (!event.isPrimary || event.button !== 0 || window.innerWidth <= 920
        || (event.target as HTMLElement).closest('.reset-position')) {
      return;
    }

    const panel = this.filterPanel?.nativeElement;
    if (!panel) {
      return;
    }

    const bounds = panel.getBoundingClientRect();
    this.filterPosition = { x: bounds.left, y: bounds.top };
    this.filterDragState = {
      pointerId: event.pointerId,
      offsetX: event.clientX - bounds.left,
      offsetY: event.clientY - bounds.top,
      width: bounds.width,
      height: bounds.height
    };
    this.filterDragging = true;
    panel.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  protected resetFilterPosition(): void {
    this.filterPosition = null;
    this.filterDragState = null;
    this.filterDragging = false;
  }

  protected moveFilter(event: PointerEvent): void {
    const drag = this.filterDragState;
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }

    this.filterPosition = this.clampFilterPosition(
      event.clientX - drag.offsetX,
      event.clientY - drag.offsetY,
      drag.width,
      drag.height
    );
  }

  protected stopFilterDrag(event: PointerEvent): void {
    if (event.pointerId === this.filterDragState?.pointerId) {
      this.filterDragState = null;
      this.filterDragging = false;
    }
  }

  protected pinPreview(listing: ListingResponse, event: MouseEvent): void {
    if (this.previewPositions.has(listing.id)) {
      return;
    }
    const tooltip = (event.currentTarget as HTMLElement).closest('.item-tooltip');
    const bounds = tooltip?.getBoundingClientRect();
    const offset = this.pinnedListings.length * 24;
    this.previewPositions.set(listing.id, bounds
      ? this.clampPreviewPosition(bounds.left + offset, bounds.top + offset, bounds.width, bounds.height)
      : { x: 16 + offset, y: 88 + offset });
    this.pinnedListings = [...this.pinnedListings, listing];
    this.frontPreviewId = listing.id;
  }

  protected unpinPreview(listingId: number): void {
    this.pinnedListings = this.pinnedListings.filter((listing) => listing.id !== listingId);
    this.previewPositions.delete(listingId);
    if (this.frontPreviewId === listingId) {
      this.frontPreviewId = this.pinnedListings.at(-1)?.id ?? null;
    }
    if (this.previewDragState?.listingId === listingId) {
      this.previewDragState = null;
      this.previewDraggingId = null;
    }
  }

  protected bringPreviewToFront(listingId: number): void {
    this.frontPreviewId = listingId;
  }

  protected startPreviewDrag(listingId: number, event: PointerEvent): void {
    this.frontPreviewId = listingId;
    if ((event.target as Element).closest('.preview-close')) {
      return;
    }
    if (!event.isPrimary || event.button !== 0) {
      return;
    }
    const panel = event.currentTarget as HTMLElement;
    const bounds = panel.getBoundingClientRect();
    this.previewDragState = {
      listingId,
      pointerId: event.pointerId,
      offsetX: event.clientX - bounds.left,
      offsetY: event.clientY - bounds.top,
      width: bounds.width,
      height: bounds.height
    };
    this.previewDraggingId = listingId;
    panel.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  @HostListener('window:pointermove', ['$event'])
  protected movePreview(event: PointerEvent): void {
    const drag = this.previewDragState;
    if (drag && event.pointerId === drag.pointerId) {
      this.previewPositions.set(drag.listingId, this.clampPreviewPosition(
        event.clientX - drag.offsetX,
        event.clientY - drag.offsetY,
        drag.width,
        drag.height
      ));
    }
  }

  @HostListener('window:pointerup', ['$event'])
  @HostListener('window:pointercancel', ['$event'])
  protected stopPreviewDrag(event: PointerEvent): void {
    if (event.pointerId === this.previewDragState?.pointerId) {
      this.previewDragState = null;
      this.previewDraggingId = null;
    }
  }

  protected movePreviewWithKeyboard(listingId: number, event: KeyboardEvent): void {
    if (event.key === 'Escape') {
      this.unpinPreview(listingId);
      event.preventDefault();
      return;
    }
    const shifts: Record<string, [number, number]> = {
      ArrowLeft: [-24, 0], ArrowRight: [24, 0], ArrowUp: [0, -24], ArrowDown: [0, 24]
    };
    const shift = shifts[event.key];
    const panel = (event.currentTarget as HTMLElement).closest<HTMLElement>('.pinned-preview');
    const position = this.previewPositions.get(listingId);
    if (!shift || !panel || !position) {
      return;
    }
    const bounds = panel.getBoundingClientRect();
    this.previewPositions.set(listingId, this.clampPreviewPosition(
      position.x + shift[0],
      position.y + shift[1],
      bounds.width,
      bounds.height
    ));
    event.preventDefault();
  }

  private clampPreviewPosition(x: number, y: number, width: number, height: number): { x: number; y: number } {
    return {
      x: Math.min(Math.max(12, window.innerWidth - width - 12), Math.max(12, x)),
      y: Math.min(Math.max(12, window.innerHeight - height - 12), Math.max(12, y))
    };
  }

  protected moveFilterWithKeyboard(event: KeyboardEvent): void {
    if (window.innerWidth <= 920) {
      return;
    }

    if (event.key === 'Home') {
      this.filterPosition = null;
      event.preventDefault();
      return;
    }

    const shifts: Record<string, [number, number]> = {
      ArrowLeft: [-24, 0],
      ArrowRight: [24, 0],
      ArrowUp: [0, -24],
      ArrowDown: [0, 24]
    };
    const shift = shifts[event.key];
    const panel = this.filterPanel?.nativeElement;
    if (!shift || !panel) {
      return;
    }

    const bounds = panel.getBoundingClientRect();
    this.filterPosition = this.clampFilterPosition(
      bounds.left + shift[0],
      bounds.top + shift[1],
      bounds.width,
      bounds.height
    );
    event.preventDefault();
  }

  @HostListener('window:resize')
  protected keepFilterInViewport(): void {
    this.pinnedPreviewElements?.forEach(({ nativeElement: preview }) => {
      const listingId = Number(preview.dataset['listingId']);
      const position = this.previewPositions.get(listingId);
      if (!position) {
        return;
      }
      const bounds = preview.getBoundingClientRect();
      this.previewPositions.set(listingId, this.clampPreviewPosition(
        position.x, position.y, bounds.width, bounds.height
      ));
    });
    const panel = this.filterPanel?.nativeElement;
    if (!panel || !this.filterPosition || window.innerWidth <= 920) {
      return;
    }

    const bounds = panel.getBoundingClientRect();
    this.filterPosition = this.clampFilterPosition(
      this.filterPosition.x,
      this.filterPosition.y,
      bounds.width,
      bounds.height
    );
  }

  private clampFilterPosition(x: number, y: number, width: number, height: number): { x: number; y: number } {
    const maxX = Math.max(12, window.innerWidth - width - 12);
    const maxY = Math.max(83, window.innerHeight - height - 12);
    return {
      x: Math.min(maxX, Math.max(12, x)),
      y: Math.min(maxY, Math.max(83, y))
    };
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

  protected requiredProfessions(listing: ListingResponse): string | null {
    return itemRequiredProfessions(listing.itemStats);
  }

  protected lastAvailableDuring(listing: ListingResponse): string | null {
    return itemLastAvailableDuring(listing.itemStats);
  }

  protected itemTypes(types: LookupResponse[]): LookupResponse[] {
    return marketItemTypes(types);
  }

  protected enhancementLabel(listing: ListingResponse): string {
    return hasEnhancementLevel(listing.itemType.name, listing.itemName) ? `+${listing.enhancementLevel}` : '-';
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
