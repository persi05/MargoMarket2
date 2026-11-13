import { ListingService } from '../../core/services/listing.service';
import { formatGold, formatPln, formatGoldRate } from '../../core/utils/gold-format';
import { DatePipe } from '@angular/common';
import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { ListingResponse } from '../../core/models/api.models';
import { ItemDescriptionPipe } from '../../core/pipes/item-description.pipe';
import { formatListingPrice } from '../../core/utils/price-format';
import { itemLastAvailableDuring, itemRequiredProfessions, itemStatLines } from '../../core/utils/item-stats';
import { hasEnhancementLevel } from '../../core/utils/item-types';
import { PrivateDetailComponent } from '../private-detail/private-detail.component';

@Component({
  selector: 'mm-listing-card',
  standalone: true,
  imports: [DatePipe, RouterLink, ItemDescriptionPipe, PrivateDetailComponent],
  templateUrl: './listing-card.component.html',
  styleUrl: './listing-card.component.css'
})
export class ListingCardComponent {
  private readonly listings=inject(ListingService);
  protected goldBusy=false;protected goldError='';
  protected get goldLabel(){return formatGold(this.listing.goldAmount!);}
  protected get goldRate(){return formatGoldRate(this.listing.goldRateMode,this.listing.rateGoldUnits,this.listing.ratePriceMinor);}
  protected get goldPrice(){return formatPln(this.listing.priceMinor);}
  protected withdrawGold(){if(this.goldBusy)return;this.goldBusy=true;this.listings.withdraw(this.listing.id).subscribe({next:l=>{this.listing=l;this.goldBusy=false},error:()=>{this.goldError='Nie udało się zakończyć oferty.';this.goldBusy=false}});}

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
    if (this.listing.status === 'withdrawn') return 'Zakończone bez sprzedaży';
    if (this.listing.status === 'archived') return 'Zarchiwizowane';
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

  protected get enhancementLabel(): string {
    return hasEnhancementLevel((this.listing.itemType?.name ?? ''), this.listing.itemName) ? `+${this.listing.enhancementLevel}` : '-';
  }

  protected get requiredProfessions(): string | null {
    return itemRequiredProfessions(this.listing.itemStats);
  }

  protected get lastAvailableDuring(): string | null {
    return itemLastAvailableDuring(this.listing.itemStats);
  }

  protected markImageFailed(): void {
    this.imageFailed = true;
  }

  protected get rarityClass(): string {
    return `rarity-${this.rarityKey}`;
  }

  private get rarityKey(): string {
    const rarity = this.normalize((this.listing.rarity?.name ?? ''));

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
