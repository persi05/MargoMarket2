import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { ListingResponse } from '../../core/models/api.models';
import { formatListingPrice } from '../../core/utils/price-format';
import { itemStatLines } from '../../core/utils/item-stats';

@Component({
  selector: 'mm-listing-card',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './listing-card.component.html',
  styleUrl: './listing-card.component.css'
})
export class ListingCardComponent {
  @Input({ required: true }) listing!: ListingResponse;
  @Input() showFavorite = false;
  @Input() showOwnerActions = false;
  @Input() favoriteMode = false;
  @Input() isFavorite = false;
  @Input() favoriteBusy = false;
  @Input() allowInactiveDelete = false;
  @Input() showSoldAt = false;

  @Output() favorite = new EventEmitter<ListingResponse>();
  @Output() removeFavorite = new EventEmitter<ListingResponse>();
  @Output() sold = new EventEmitter<ListingResponse>();
  @Output() deleteListing = new EventEmitter<ListingResponse>();

  protected imageFailed = false;

  protected get isActive(): boolean {
    return this.listing.status === 'active';
  }

  protected get isSold(): boolean {
    return this.listing.status === 'sold';
  }

  protected get statusLabel(): string {
    if (this.isActive) {
      return 'Aktywne';
    }

    if (this.isSold) {
      return 'Sprzedane';
    }

    if (this.listing.status === 'deleted' || this.listing.status === 'removed') {
      return 'Usunięte';
    }

    return 'Nieaktywne';
  }

  protected get canDelete(): boolean {
    return this.isActive || this.allowInactiveDelete;
  }

  protected get priceLabel(): string {
    return formatListingPrice(this.listing.price, this.listing.currency.name);
  }

  protected get showImage(): boolean {
    return Boolean(this.listing.iconUrl) && !this.imageFailed;
  }

  protected get statLines() {
    return itemStatLines(this.listing.itemStats);
  }

  protected markImageFailed(): void {
    this.imageFailed = true;
  }

  protected get rarityClass(): string {
    return `rarity-${this.rarityKey}`;
  }

  private get rarityKey(): string {
    const rarity = this.normalize(this.listing.rarity.name);

    if (rarity.includes('legendarn')) {
      return 'legendary';
    }

    if (rarity.includes('heroiczn')) {
      return 'heroic';
    }

    if (rarity.includes('unikat')) {
      return 'unique';
    }

    if (rarity.includes('ulepszon')) {
      return 'upgraded';
    }

    return 'normal';
  }

  private normalize(value: string): string {
    return value
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase();
  }
}
