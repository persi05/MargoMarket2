import { DatePipe, DecimalPipe } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

import { ListingResponse } from '../../core/models/api.models';

@Component({
  selector: 'mm-listing-card',
  standalone: true,
  imports: [DatePipe, DecimalPipe],
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

  @Output() favorite = new EventEmitter<ListingResponse>();
  @Output() removeFavorite = new EventEmitter<ListingResponse>();
  @Output() sold = new EventEmitter<ListingResponse>();
  @Output() deleteListing = new EventEmitter<ListingResponse>();
}
