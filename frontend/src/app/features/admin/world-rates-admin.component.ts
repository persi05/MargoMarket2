import { DatePipe } from '@angular/common';
import { Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { finalize } from 'rxjs';

import { WorldRates, WorldRatesRequest, WorldType } from '../../core/models/api.models';
import { PriceService } from '../../core/services/price.service';
import { WORLD_TYPE_LABELS, goldRateInput, parseGoldRate, parsePlnInput, plnInput } from '../../core/utils/world-rates';

interface WorldRow {
  id?: number;
  name: string;
  worldType: WorldType;
  gold: string;
  upgrade: string;
  essence: string;
  updatedAt: string | null;
  saving: boolean;
  message: string;
  error: boolean;
}

@Component({
  selector: 'mm-world-rates-admin', standalone: true, imports: [FormsModule, DatePipe],
  template: `
    <section class="worlds" aria-labelledby="worlds-title">
      <h2 id="worlds-title">Światy i przeliczniki</h2>
      <p>Kursy służą do sugerowania cen. Światy publiczne z przenosinami tworzą jeden wspólny rynek,
        pozostałe są liczone osobno. Puste pole oznacza brak przelicznika.</p>
      @if (loadError()) { <p class="error" role="alert">{{ loadError() }}</p> }
      <form class="scales" (submit)="$event.preventDefault(); saveScales()">
        <label for="scales-price">Cena 100 SŁ (zł)</label>
        <input id="scales-price" name="scales" inputmode="decimal" placeholder="brak" [(ngModel)]="scales" [disabled]="scalesSaving()">
        <button type="submit" [disabled]="scalesSaving()">Zapisz</button>
        @if (scalesMessage()) { <span [class.ok]="!scalesError()" [class.error-text]="scalesError()" role="status">{{ scalesMessage() }}</span> }
        <small>Wspólna dla wszystkich światów. Służy do przeliczania cen związanych i niezwiązanych kosztem odwiązania; puste pole wyłącza przeliczanie.</small>
      </form>
      <div class="table-wrap">
        <table>
          <thead><tr>
            <th scope="col">Świat</th><th scope="col">Typ</th><th scope="col">1 zł = … m złota</th>
            <th scope="col">1 mln pkt ulepy (zł)</th><th scope="col">1 esencja (zł)</th><th scope="col"><span class="sr-only">Akcje</span></th>
          </tr></thead>
          <tbody>
            @for (row of rows(); track row.id ?? 'new') {
              <tr>
                <td>
                  @if (row.id === undefined) {
                    <input [(ngModel)]="row.name" placeholder="Nazwa świata" aria-label="Nazwa nowego świata" maxlength="100">
                  } @else { <strong>{{ row.name }}</strong> }
                </td>
                <td>
                  <select [(ngModel)]="row.worldType" [attr.aria-label]="'Typ świata ' + row.name">
                    @for (type of types; track type) { <option [value]="type">{{ labels[type] }}</option> }
                  </select>
                </td>
                <td><input [(ngModel)]="row.gold" inputmode="decimal" placeholder="np. 18" [attr.aria-label]="'Kurs złota ' + row.name"></td>
                <td><input [(ngModel)]="row.upgrade" inputmode="decimal" placeholder="np. 100" [attr.aria-label]="'Cena ulepy ' + row.name"></td>
                <td><input [(ngModel)]="row.essence" inputmode="decimal" placeholder="np. 1" [attr.aria-label]="'Cena esencji ' + row.name"></td>
                <td class="actions">
                  <button type="button" (click)="save(row)" [disabled]="row.saving">
                    {{ row.saving ? 'Zapisywanie…' : row.id === undefined ? 'Dodaj świat' : 'Zapisz' }}
                  </button>
                  @if (row.message) { <span [class.error-text]="row.error" [class.ok]="!row.error" role="status">{{ row.message }}</span> }
                  @else if (row.updatedAt) { <span class="date">Zmieniono {{ row.updatedAt | date:'dd.MM.yyyy HH:mm' }}</span> }
                </td>
              </tr>
            }
          </tbody>
        </table>
      </div>
    </section>
  `,
  styles: [`
    .worlds { padding: 1.5rem; border: 1px solid rgba(151,121,87,.42); border-radius: 12px; background: #261c17; color: #f1ede4; }
    h2 { margin: 0 0 .6rem; } p { line-height: 1.6; color: #b9ad9f; }
    .table-wrap { overflow-x: auto; } table { width: 100%; border-collapse: collapse; min-width: 760px; }
    th, td { padding: .5rem; text-align: left; border-bottom: 1px solid rgba(151,121,87,.25); vertical-align: middle; }
    th { color: #b9ad9f; font-size: .85rem; font-weight: 600; }
    input, select { width: 100%; box-sizing: border-box; padding: .45rem .55rem; background: #1b1411; color: #f1ede4; border: 1px solid rgba(151,121,87,.5); border-radius: 6px; font: inherit; }
    button { padding: .5rem .9rem; background: #31542b; color: #f1ede4; border: 1px solid #729661; border-radius: 7px; font: inherit; font-weight: 700; cursor: pointer; white-space: nowrap; }
    button:disabled { opacity: .6; cursor: default; } button:focus-visible, input:focus-visible, select:focus-visible { outline: 2px solid #b4ce93; outline-offset: 2px; }
    .scales { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem .75rem; margin: 0 0 1.2rem; }
    .scales label { font-weight: 700; } .scales input { width: 8rem; } .scales small { flex-basis: 100%; color: #b9ad9f; }
    .actions { display: flex; flex-wrap: wrap; align-items: center; gap: .5rem; }
    .ok { color: #b4ce93; } .error-text { color: #ffb4a6; } .date { color: #b9ad9f; font-size: .8rem; }
    .error { background: #522820; color: #ffdad2; border-radius: 7px; padding: .75rem; }
    .sr-only { position: absolute; width: 1px; height: 1px; overflow: hidden; clip: rect(0 0 0 0); }
  `]
})
export class WorldRatesAdminComponent implements OnInit {
  private readonly prices = inject(PriceService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly types: WorldType[] = ['TRANSFERABLE', 'ISOLATED', 'PRIVATE'];
  protected readonly labels = WORLD_TYPE_LABELS;
  protected readonly rows = signal<WorldRow[]>([]);
  protected readonly loadError = signal('');
  protected scales = '';
  protected readonly scalesSaving = signal(false);
  protected readonly scalesMessage = signal('');
  protected readonly scalesError = signal(false);

  ngOnInit(): void {
    this.prices.worlds().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: worlds => this.rows.set([...worlds.map(toRow), newRow()]),
      error: () => this.loadError.set('Nie udało się wczytać światów.')
    });
    this.prices.settings().pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: settings => this.scales = plnInput(settings.scalesHundredPriceGrosze),
      error: () => this.loadError.set('Nie udało się wczytać ceny SŁ.')
    });
  }

  protected saveScales(): void {
    const scalesHundredPriceGrosze = parsePlnInput(this.scales);
    if (scalesHundredPriceGrosze === undefined) {
      this.scalesError.set(true);
      this.scalesMessage.set('Wpisz liczbę, np. 1,5.');
      return;
    }
    this.scalesSaving.set(true);
    this.scalesMessage.set('');
    this.prices.saveSettings({ scalesHundredPriceGrosze }).pipe(
      finalize(() => this.scalesSaving.set(false)), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: settings => {
        this.scales = plnInput(settings.scalesHundredPriceGrosze);
        this.scalesError.set(false);
        this.scalesMessage.set('Zapisano.');
      },
      error: (response: HttpErrorResponse) => {
        this.scalesError.set(true);
        this.scalesMessage.set(response.error?.message || 'Nie udało się zapisać.');
      }
    });
  }

  protected save(row: WorldRow): void {
    const goldPerPln = parseGoldRate(row.gold);
    const upgradeMillionPriceGrosze = parsePlnInput(row.upgrade);
    const essencePriceGrosze = parsePlnInput(row.essence);
    if (goldPerPln === undefined || upgradeMillionPriceGrosze === undefined || essencePriceGrosze === undefined) {
      this.update(row, { message: 'Wpisz liczby, np. 18 albo 0,5.', error: true });
      return;
    }
    if (row.id === undefined && row.name.trim().length < 2) {
      this.update(row, { message: 'Podaj nazwę świata.', error: true });
      return;
    }
    const request: WorldRatesRequest = { worldType: row.worldType, goldPerPln, upgradeMillionPriceGrosze, essencePriceGrosze };
    if (row.id === undefined) request.name = row.name.trim();
    this.update(row, { saving: true, message: '', error: false });
    this.prices.saveWorld(request, row.id).pipe(
      finalize(() => this.update(row, { saving: false })), takeUntilDestroyed(this.destroyRef)
    ).subscribe({
      next: world => {
        const saved = { ...toRow(world), message: 'Zapisano.' };
        this.rows.update(rows => {
          const updated = rows.map(r => r === row ? saved : r);
          return row.id === undefined ? [...updated.filter(r => r.id !== undefined).sort((a, b) => a.name.localeCompare(b.name, 'pl')), newRow()] : updated;
        });
      },
      error: (response: HttpErrorResponse) => this.update(row, {
        message: response.error?.message || 'Nie udało się zapisać.', error: true
      })
    });
  }

  private update(row: WorldRow, changes: Partial<WorldRow>): void {
    Object.assign(row, changes);
    this.rows.update(rows => [...rows]);
  }
}

function toRow(world: WorldRates): WorldRow {
  return {
    id: world.id, name: world.name, worldType: world.worldType, gold: goldRateInput(world.goldPerPln),
    upgrade: plnInput(world.upgradeMillionPriceGrosze), essence: plnInput(world.essencePriceGrosze),
    updatedAt: world.ratesUpdatedAt, saving: false, message: '', error: false
  };
}

function newRow(): WorldRow {
  return { name: '', worldType: 'PRIVATE', gold: '', upgrade: '', essence: '', updatedAt: null, saving: false, message: '', error: false };
}
