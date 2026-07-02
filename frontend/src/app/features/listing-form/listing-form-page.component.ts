import { AsyncPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { catchError, combineLatest, debounceTime, finalize, of, startWith, switchMap } from 'rxjs';

import { ApiError, DictionariesResponse, ItemResponse, LookupResponse } from '../../core/models/api.models';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ItemService } from '../../core/services/item.service';
import { ListingService } from '../../core/services/listing.service';
import { itemStatLines } from '../../core/utils/item-stats';
import { parseListingPrice } from '../../core/utils/price-format';

@Component({
  selector: 'mm-listing-form-page',
  standalone: true,
  imports: [AsyncPipe, ReactiveFormsModule],
  templateUrl: './listing-form-page.component.html',
  styleUrl: './listing-form-page.component.css'
})
export class ListingFormPageComponent {
  private readonly catalogTypeLabels = [
    { label: 'Broń', aliases: ['bron'] },
    { label: 'Buty', aliases: ['buty'] },
    { label: 'Dwuręczne', aliases: ['dwureczne'] },
    { label: 'Dystansowe', aliases: ['dystansowe'] },
    { label: 'Hełm', aliases: ['helm'] },
    { label: 'Inne', aliases: ['inne'] },
    { label: 'Jednoręczne', aliases: ['jednoreczne'] },
    { label: 'Konsumpcyjne', aliases: ['konsumpcyjne', 'konsumcyjne'] },
    { label: 'Naszyjniki', aliases: ['naszyjnik', 'naszyjniki'] },
    { label: 'Neutralne', aliases: ['neutralne'] },
    { label: 'Orby', aliases: ['orby'] },
    { label: 'Pierścień', aliases: ['pierscien'] },
    { label: 'Pomocnicze', aliases: ['pomocnicze'] },
    { label: 'Półtoraręczne', aliases: ['poltorareczne'] },
    { label: 'Różdżki', aliases: ['rozdzki'] },
    { label: 'Rękawice', aliases: ['rekawice'] },
    { label: 'Strzały', aliases: ['strzaly'] },
    { label: 'Tarcza', aliases: ['tarcza'] },
    { label: 'Zbroja', aliases: ['zbroja'] },
    { label: 'Ulepszenie', aliases: ['ulepszenia', 'ulepszenie'] }
  ];

  private readonly fb = inject(FormBuilder);
  private readonly listingService = inject(ListingService);
  private readonly itemService = inject(ItemService);
  private readonly router = inject(Router);
  private readonly dictionaryService = inject(DictionaryService);

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
    itemCatalogLevel: this.fb.nonNullable.control(''),
    itemTypeId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    level: this.fb.nonNullable.control(1, [Validators.required, Validators.min(1), Validators.max(300)]),
    enhancementLevel: this.fb.nonNullable.control(0, [Validators.required, Validators.min(0), Validators.max(5)]),
    rarityId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    price: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(24)]),
    currencyId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    serverId: this.fb.nonNullable.control(0, [Validators.required, Validators.min(1)]),
    contact: this.fb.nonNullable.control('', [Validators.required, Validators.maxLength(50)])
  });

  protected readonly itemResults$ = combineLatest([
    this.form.controls.itemName.valueChanges.pipe(startWith(this.form.controls.itemName.value)),
    this.form.controls.itemCatalogTypeId.valueChanges.pipe(startWith(this.form.controls.itemCatalogTypeId.value)),
    this.form.controls.itemCatalogLevel.valueChanges.pipe(startWith(this.form.controls.itemCatalogLevel.value))
  ]).pipe(
    debounceTime(160),
    switchMap(([search, itemTypeId, level]) => this.itemService.search({
      search,
      itemTypeId,
      level,
      limit: 60
    }).pipe(
      catchError(() => of([]))
    ))
  );

  constructor() {
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
      delete this.serverErrors['price'];
    });

    this.form.controls.currencyId.valueChanges.subscribe(() => {
      delete this.serverErrors['price'];
    });
  }

  selectItem(item: ItemResponse, dictionaries: DictionariesResponse): void {
    this.selectedItem = item;
    const locksEnhancementLevel = this.isSkrytka(item);
    this.form.patchValue({
      itemId: item.id,
      itemName: item.name,
      itemTypeId: this.lookupId(dictionaries.itemTypes, item.itemType.name),
      level: item.level,
      enhancementLevel: 0,
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

  submit(): void {
    this.error = '';
    this.clearServerErrors();

    if (this.form.invalid || this.loading) {
      this.form.markAllAsTouched();
      this.error = 'Uzupełnij wymagane pola i popraw zaznaczone błędy.';
      return;
    }

    this.loading = true;

    const raw = this.form.getRawValue();
    const currencyName = this.selectedCurrencyName();
    const price = currencyName ? parseListingPrice(raw.price, currencyName) : null;
    if (price === null || price > 2_000_000_000) {
      this.serverErrors['price'] = currencyName?.toLowerCase() === 'w grze'
        ? 'Wpisz cenę jako liczbę albo skrót, np. 500m lub 2g.'
        : 'Wpisz poprawną cenę liczbową.';
      this.form.controls.price.markAsTouched();
      this.loading = false;
      return;
    }

    const payload = {
      itemId: raw.itemId,
      itemName: raw.itemName,
      itemTypeId: raw.itemTypeId,
      level: raw.level,
      enhancementLevel: raw.enhancementLevel,
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
    return this.catalogTypeLabels.flatMap(({ label, aliases }) => {
      const type = this.findCatalogType(itemTypes, aliases);
      return type ? [{ ...type, name: label }] : [];
    });
  }

  currencyLabel(name: string): string {
    return name.trim().toLowerCase() === 'pln' ? 'PLN' : name;
  }

  pricePlaceholder(): string {
    return this.isGoldCurrencySelected() ? 'Np. 500m albo 2g' : 'Wpisz cenę';
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
    return this.selectedItem ? this.isSkrytka(this.selectedItem) : false;
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
      Object.assign(this.serverErrors, apiError.errors);
      Object.keys(apiError.errors).forEach((field) => this.form.get(field)?.markAsTouched());
      this.error = 'Backend odrzucił formularz. Popraw zaznaczone pola.';
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

  private findCatalogType(itemTypes: LookupResponse[], aliases: string[]): LookupResponse | undefined {
    return itemTypes.find((type) => aliases.includes(this.normalizeTypeName(type.name)));
  }

  private normalizeTypeName(name: string): string {
    return name
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '');
  }

  private isSkrytka(item: ItemResponse): boolean {
    return this.normalizeTypeName(item.name).includes('skrytk');
  }
}
