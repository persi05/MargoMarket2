import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { SaleModeComponent } from '../../shared/sale-mode/sale-mode.component';
import { PriceSuggestionComponent, SuggestedPrice, roundGold } from '../../shared/price-suggestion/price-suggestion.component';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, finalize, of, startWith, switchMap } from 'rxjs';

import { ApiError, DictionariesResponse, ItemResponse, LookupResponse } from '../../core/models/api.models';
import { ItemDescriptionPipe } from '../../core/pipes/item-description.pipe';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ItemService } from '../../core/services/item.service';
import { ListingService } from '../../core/services/listing.service';
import { itemLastAvailableDuring, itemRequiredProfessions, itemStatLines } from '../../core/utils/item-stats';
import { canBindItem, hasEnhancementLevel, marketItemTypes } from '../../core/utils/item-types';
import { MAX_GOLD_LISTING_PRICE, goldPriceInput, parseListingPrice } from '../../core/utils/price-format';
import { AuctionService } from '../../core/services/auction.service';
import { goldMillionsInput, parseGoldMillions, parsePln } from '../../core/utils/gold-format';

/** Gold prices below this are almost always a typo (most offers are 100m and more). */
const LOW_GOLD_PRICE = 1_000_000;

@Component({
  selector: 'mm-listing-form-page',
  standalone: true,
  imports: [PageHeaderComponent, SaleModeComponent, PriceSuggestionComponent, AsyncPipe, ReactiveFormsModule, ItemDescriptionPipe],
  templateUrl: './listing-form-page.component.html',
  styleUrl: './listing-form-page.component.css'
})
export class ListingFormPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  private readonly itemService = inject(ItemService);
  private readonly router = inject(Router);
  private readonly dictionaryService = inject(DictionaryService);
  private readonly auctions = inject(AuctionService);
  private readonly route = inject(ActivatedRoute);
  protected auctionMode = this.route.snapshot.queryParamMap.get('mode') === 'auction' || this.route.snapshot.paramMap.has('id');
  protected auctionEditId = Number(this.route.snapshot.paramMap.get('id')) || null;
  protected auctionsEnabled = false;
  protected plnAuctionsEnabled = false;
  protected auctionEnd = new Date(Date.now() + 24 * 3600_000 - new Date().getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  protected auctionDescription = '';

  protected get auctionMaxEnd(): string {
    const end = new Date(Date.now() + 7 * 24 * 3600_000);
    return new Date(end.getTime() - end.getTimezoneOffset() * 60000).toISOString().slice(0, 16);
  }

  protected readonly dictionaries$ = this.dictionaryService.getAll();
  protected dictionaries: DictionariesResponse | null = null;
  protected loading = false;
  protected error = '';
  protected selectedItem: ItemResponse | null = null;
  protected hoveredCatalogItem: ItemResponse | null = null;
  protected tooltipX = 0;
  protected tooltipY = 0;
  protected readonly serverErrors: Record<string, string> = {};

  protected readonly brokenCatalogImageIds = new Set<number>();

  protected readonly form = this.fb.group({
    itemId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    itemName: this.fb.nonNullable.control('', [Validators.required, Validators.minLength(3), Validators.maxLength(255)]),
    itemCatalogTypeId: this.fb.nonNullable.control(''),
    itemCatalogMinLevel: this.fb.nonNullable.control(''),
    itemCatalogMaxLevel: this.fb.nonNullable.control(''),
    itemTypeId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    level: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1), Validators.max(300)]),
    enhancementLevel: this.fb.nonNullable.control(0, [Validators.required, Validators.min(0), Validators.max(5)]),
    bound: this.fb.nonNullable.control(false),
    rarityId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    price: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(24)]),
    currencyId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    serverId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    contact: this.fb.nonNullable.control('')
  });

  protected readonly itemResults$ = combineLatest([
    this.form.controls.itemName.valueChanges.pipe(startWith(this.form.controls.itemName.value)),
    this.form.controls.itemCatalogTypeId.valueChanges.pipe(startWith(this.form.controls.itemCatalogTypeId.value)),
    this.form.controls.itemCatalogMinLevel.valueChanges.pipe(startWith(this.form.controls.itemCatalogMinLevel.value)),
    this.form.controls.itemCatalogMaxLevel.valueChanges.pipe(startWith(this.form.controls.itemCatalogMaxLevel.value))
  ]).pipe(
    debounceTime(160),
    switchMap(([search, itemTypeId, minLevel, maxLevel]) => this.itemService.search({
      search,
      itemTypeId,
      minLevel,
      maxLevel,
      limit: 60
    }).pipe(
      catchError(() => of([]))
    ))
  );

  constructor() {
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(params => {
      if (!this.auctionEditId) this.auctionMode = params.get('mode') === 'auction';
    });
    this.auctions.config().subscribe(c => { this.auctionsEnabled = c.enabled; this.plnAuctionsEnabled = c.plnEnabled; });
    if (this.auctionEditId) this.auctions.get(this.auctionEditId).subscribe({
      next: a => {
        if (!a.owner || a.bidCount || a.status !== 'ACTIVE') { this.error = 'Tej aukcji nie można edytować.'; return; }
        const l = a.listing;
        if (l.listingKind !== 'ITEM' || !l.itemId) return;
        this.selectedItem = {id:l.itemId,externalId:0,name:l.itemName,iconUrl:l.iconUrl ?? '',level:l.level,itemType:l.itemType,rarity:l.rarity,description:l.itemDescription,stats:l.itemStats};
        this.form.patchValue({itemId:l.itemId,itemName:l.itemName,itemTypeId:l.itemType.id,rarityId:l.rarity.id,level:l.level,
          enhancementLevel:l.enhancementLevel,bound:l.bound,serverId:l.server.id,currencyId:l.currency.id,contact:l.contact || 'Czat MargoMarket',
          price: a.startingAmount === null ? '' : (a.currency === 'PLN' ? (a.startingAmount / 100).toFixed(2) : goldMillionsInput(a.startingAmount))});
        this.auctionDescription = l.description || '';
        const end = new Date(a.endsAt);
        this.auctionEnd = new Date(end.getTime()-end.getTimezoneOffset()*60000).toISOString().slice(0,16);
      }, error: () => this.error = 'Nie udało się pobrać aukcji do edycji.'
    });
    this.dictionaries$.subscribe((dictionaries) => {
      this.dictionaries = dictionaries;
    });

    this.form.controls.itemTypeId.disable();
    this.form.controls.level.disable();
    this.form.controls.rarityId.disable();

    this.form.controls.itemName.valueChanges.subscribe((name) => {
      if (this.selectedItem && name !== this.selectedItem.name) {
        this.selectedItem = null;
        this.form.controls.enhancementLevel.enable({ emitEvent: false });
        this.form.patchValue({
          itemId: 0,
          itemTypeId: 0,
          level: 1,
          enhancementLevel: 0,
          rarityId: 0
        }, { emitEvent: false });
      }
    });

    this.form.controls.price.valueChanges.subscribe(() => {
      this.lowGoldConfirm = null;
      delete this.serverErrors['price'];
    });

    this.form.controls.currencyId.valueChanges.subscribe(() => {
      this.lowGoldConfirm = null;
      delete this.serverErrors['price'];
    });
  }

  selectItem(item: ItemResponse, dictionaries: DictionariesResponse): void {
    this.hideItemTooltip();
    this.selectedItem = item;
    const locksEnhancementLevel = !hasEnhancementLevel(item.itemType.name, item.name);
    const locksBinding = !canBindItem(item.itemType.name);
    this.form.patchValue({
      itemId: item.id,
      itemName: item.name,
      itemTypeId: this.lookupId(dictionaries.itemTypes, item.itemType.name),
      level: item.level,
      enhancementLevel: 0,
      bound: locksBinding ? false : this.form.controls.bound.value,
      rarityId: this.lookupId(dictionaries.rarities, item.rarity.name)
    });
    if (locksEnhancementLevel) {
      this.form.controls.enhancementLevel.disable();
    } else {
      this.form.controls.enhancementLevel.enable();
    }
    this.form.controls.itemId.markAsDirty();
    this.form.controls.itemId.markAsTouched();
  }

  /** Fills the price with the suggestion, in gold when gold is chosen and fits, otherwise in PLN. */
  useSuggestedPrice(price: SuggestedPrice): void {
    if (this.isGoldCurrencySelected() && price.gold !== null && (this.auctionMode || roundGold(price.gold) <= MAX_GOLD_LISTING_PRICE)) {
      this.form.controls.price.setValue(goldPriceInput(roundGold(price.gold)));
    } else {
      const pln = this.dictionaries?.currencies.find(currency => currency.name.trim().toLowerCase() === 'pln');
      if (pln) this.form.controls.currencyId.setValue(pln.id);
      this.form.controls.price.setValue(String(Math.max(1, Math.round(price.grosze / 100))));
    }
    this.form.controls.price.markAsDirty();
  }

  toggleBound(): void {
    if (this.isBindingLocked()) {
      return;
    }
    const control = this.form.controls.bound;
    control.setValue(!control.value);
    control.markAsDirty();
  }

  submit(): void {
    this.error = '';
    this.clearServerErrors();

    if (this.loading) return;
    if (this.auctionMode) this.validateAuctionFields();
    if (this.form.invalid || Object.keys(this.serverErrors).length) {
      this.form.markAllAsTouched();
      this.error = this.auctionMode ? '' : 'Uzupełnij wymagane pola i popraw zaznaczone błędy.';
      return;
    }

    this.loading = true;

    const raw = this.form.getRawValue();
    if (this.auctionMode) {
      const currency = this.isGoldCurrencySelected() ? 'GOLD' as const : 'PLN' as const;
      const startingAmount = currency === 'GOLD' ? parseGoldMillions(raw.price) : parsePln(raw.price);
      const end = new Date(this.auctionEnd);
      if (startingAmount === null || !Number.isFinite(end.getTime())) { this.loading = false; return; }
      if (currency === 'GOLD' && this.needsLowGoldConfirm(startingAmount)) { this.loading = false; return; }
      const request = {itemId:raw.itemId, serverId:raw.serverId, enhancementLevel:raw.enhancementLevel, bound:raw.bound,
        currency, startingAmount, endsAt:end.toISOString(), description:this.auctionDescription, contact:raw.contact};
      (this.auctionEditId ? this.auctions.update(this.auctionEditId,request) : this.auctions.create(request))
        .pipe(finalize(() => this.loading = false)).subscribe({
          next: a => void this.router.navigate(['/auctions',a.id]),
          error: e => this.applyServerErrors(e)
        });
      return;
    }
    const currencyName = this.selectedCurrencyName();
    const price = currencyName ? parseListingPrice(raw.price, currencyName) : null;
    if (price === null) {
      this.serverErrors['price'] = currencyName?.toLowerCase() === 'w grze'
        ? 'Wpisz cenę jako liczbę albo skrót, np. 500m, 6,5g lub 6.5g (najwyżej 10 000g).'
        : 'Wpisz poprawną cenę liczbową.';
      this.form.controls.price.markAsTouched();
      this.loading = false;
      return;
    }
    if (this.isGoldCurrencySelected() && this.needsLowGoldConfirm(price)) {
      this.loading = false;
      return;
    }

    const payload = {
      itemId: raw.itemId,
      itemName: raw.itemName,
      itemTypeId: raw.itemTypeId,
      level: raw.level,
      enhancementLevel: raw.enhancementLevel,
      bound: raw.bound,
      rarityId: raw.rarityId,
      price,
      currencyId: raw.currencyId,
      serverId: raw.serverId,
      contact: raw.contact
    };

    this.listingService.create(payload).pipe(
      finalize(() => {
        this.loading = false;
      })
    ).subscribe({
      next: () => void this.router.navigate(['/dashboard']),
      error: (response: unknown) => {
        this.applyServerErrors(response);
      }
    });
  }

  protected auctionFieldChanged(field: string, value: string): void {
    if (field === 'auctionEnd') this.auctionEnd = value;
    else this.auctionDescription = value;
    delete this.serverErrors[field];
    this.error = '';
  }

  private validateAuctionFields(): void {
    const price = this.form.controls.price.value;
    if ((this.isGoldCurrencySelected() ? parseGoldMillions(price) : parsePln(price)) === null) {
      this.serverErrors['price'] = this.isGoldCurrencySelected() ? 'Wpisz dodatnią cenę, np. 500m lub 2g.' : 'Wpisz dodatnią cenę w zł, np. 25,50.';
    }
    const duration = new Date(this.auctionEnd).getTime() - Date.now();
    if (!this.auctionEnd) this.serverErrors['auctionEnd'] = 'Wybierz termin zakończenia licytacji.';
    else if (!Number.isFinite(duration) || duration < 3600_000 || duration > 7 * 24 * 3600_000) this.serverErrors['auctionEnd'] = 'Wybierz termin od 1 godziny do 7 dni od teraz.';
    if (this.auctionDescription.length > 2000) this.serverErrors['auctionDescription'] = 'Opis może mieć maksymalnie 2000 znaków.';
  }

  /** Gold amount the seller has to confirm because it is below 1m, most likely a missing "m". */
  lowGoldConfirm: number | null = null;
  private confirmedLowGold: number | null = null;

  /** First submit with a gold price under 1m only asks to confirm it; the second submit with the same amount goes through. */
  private needsLowGoldConfirm(amount: number): boolean {
    if (amount >= LOW_GOLD_PRICE || this.confirmedLowGold === amount) return false;
    if (this.lowGoldConfirm === amount) {
      this.confirmedLowGold = amount;
      this.lowGoldConfirm = null;
      return false;
    }
    this.lowGoldConfirm = amount;
    return true;
  }

  fieldError(name: string): string {
    if (this.serverErrors[name]) {
      return this.serverErrors[name];
    }

    const control = this.form.get(name);
    if (!control || !control.errors || !(control.touched || control.dirty)) {
      return '';
    }

    if (control.errors['required']) {
      return name === 'itemId' ? 'Wybierz przedmiot z katalogu.' : 'To pole jest wymagane.';
    }

    if (control.errors['minlength']) {
      return `Wpisz co najmniej ${control.errors['minlength'].requiredLength} znaki.`;
    }

    if (control.errors['maxlength']) {
      return `Maksymalnie ${control.errors['maxlength'].requiredLength} znaków.`;
    }

    if (control.errors['min']) {
      return name === 'itemId' ? 'Wybierz przedmiot z katalogu.' : `Wartość musi być co najmniej ${control.errors['min'].min}.`;
    }

    if (control.errors['max']) {
      return `Wartość nie może przekraczać ${control.errors['max'].max}.`;
    }

    return 'Nieprawidłowa wartość.';
  }

  hasFieldError(name: string): boolean {
    return Boolean(this.fieldError(name));
  }

  catalogItemTypes(itemTypes: LookupResponse[]): LookupResponse[] {
    return marketItemTypes(itemTypes);
  }

  currencyLabel(name: string): string {
    return name.trim().toLowerCase() === 'pln' ? 'zł' : name;
  }

  pricePlaceholder(): string {
    if (this.auctionMode) return this.isGoldCurrencySelected() ? 'Np. 500m lub 6,5g' : 'Np. 25,50 lub 25.50';
    return this.isGoldCurrencySelected() ? 'Np. 500m lub 6,5g' : 'Wpisz cenę';
  }

  /** After leaving the field a valid gold price is rewritten to one form, e.g. 6500m or 6.5g becomes 6,5g. */
  normalizePrice(): void {
    if (!this.isGoldCurrencySelected()) return;
    const raw = this.form.controls.price.value;
    const units = this.auctionMode ? parseGoldMillions(raw) : parseListingPrice(raw, 'w grze');
    // Auctions read a bare number as millions, so amounts under 1m keep the seller's own text.
    if (units === null || (this.auctionMode && units < 1_000_000)) return;
    const text = goldPriceInput(units);
    if (text !== raw) this.form.controls.price.setValue(text);
  }

  showCatalogImage(item: ItemResponse): boolean {
    return Boolean(item.iconUrl) && !this.brokenCatalogImageIds.has(item.id);
  }

  markCatalogImageBroken(item: ItemResponse): void {
    this.brokenCatalogImageIds.add(item.id);
  }

  statLines(item: ItemResponse) {
    return itemStatLines(item.stats);
  }

  requiredProfessions(item: ItemResponse): string | null {
    return itemRequiredProfessions(item.stats);
  }

  lastAvailableDuring(item: ItemResponse): string | null {
    return itemLastAvailableDuring(item.stats);
  }

  rarityClass(item: ItemResponse): string {
    const rarity = this.normalizeTypeName(item.rarity.name);

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

  isEnhancementLocked(): boolean {
    return this.selectedItem ? !hasEnhancementLevel(this.selectedItem.itemType.name, this.selectedItem.name) : false;
  }

  isBindingLocked(): boolean {
    return this.selectedItem ? !canBindItem(this.selectedItem.itemType.name) : false;
  }

  isGoldCurrencySelected(): boolean {
    return this.selectedCurrencyName()?.toLowerCase() === 'w grze';
  }

  showItemTooltip(item: ItemResponse, event: MouseEvent): void {
    this.hoveredCatalogItem = item;
    this.moveItemTooltip(event);
  }

  moveItemTooltip(event: MouseEvent): void {
    this.tooltipX = Math.min(event.clientX + 18, window.innerWidth - 380);
    this.tooltipY = Math.min(event.clientY + 18, window.innerHeight - 520);
  }

  hideItemTooltip(): void {
    this.hoveredCatalogItem = null;
  }

  private applyServerErrors(response: unknown): void {
    const apiError = response instanceof HttpErrorResponse ? response.error as ApiError | null : null;

    if (apiError?.errors) {
      const aliases: Record<string,string> = {startingAmount:'price', endsAt:'auctionEnd', description:'auctionDescription', currency:'currencyId'};
      for (const [field, message] of Object.entries(apiError.errors)) this.serverErrors[this.auctionMode ? (aliases[field] || field) : field] = message;
      Object.keys(apiError.errors).forEach((field) => this.form.get(field)?.markAsTouched());
      this.error = this.auctionMode ? '' : 'Backend odrzucił formularz. Popraw zaznaczone pola.';
      return;
    }

    this.error = apiError?.message || 'Nie udało się dodać ogłoszenia. Sprawdź dane i spróbuj ponownie.';
  }

  private clearServerErrors(): void {
    Object.keys(this.serverErrors).forEach((key) => {
      delete this.serverErrors[key];
    });
  }

  private selectedCurrencyName(): string | null {
    const currencyId = this.form.controls.currencyId.value;
    return this.dictionaries?.currencies.find((currency) => currency.id === currencyId)?.name || null;
  }

  private lookupId(options: LookupResponse[], name: string): number {
    return options.find((option) => option.name === name)?.id || 0;
  }

  private normalizeTypeName(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '');
  }

}
