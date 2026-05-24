import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { ListingResponse } from '../../core/models/api.models';

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

  @Output() favorite = new EventEmitter<ListingResponse>();
  @Output() removeFavorite = new EventEmitter<ListingResponse>();
  @Output() sold = new EventEmitter<ListingResponse>();
  @Output() deleteListing = new EventEmitter<ListingResponse>();

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
    const formattedPrice = new Intl.NumberFormat('pl-PL').format(this.listing.price);
    const currencyName = this.listing.currency.name.trim();

    if (currencyName.toLowerCase() === 'w grze') {
      return `${formattedPrice} złota`;
    }

    return `${formattedPrice} ${currencyName}`;
  }
}
