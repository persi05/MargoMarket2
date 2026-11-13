import { Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { ListingService } from '../../core/services/listing.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { LookupResponse } from '../../core/models/api.models';
import {
  formatGold,
  parseGoldMillions,
  goldMillionsInput,
  GoldRateMode,
  defaultGoldRateMode,
  parseGoldRate,
  goldPackageTotal,
  plnInput,
  formatPln,
} from '../../core/utils/gold-format';
@Component({
  selector: 'mm-gold-form',
  standalone: true,
  imports: [FormsModule, RouterLink],
  templateUrl: './gold-form.component.html',
  styleUrl: './gold-page.component.css',
})
export class GoldFormComponent {
  private service = inject(ListingService);
  private router = inject(Router);
  private id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id')) || null;
  protected worlds: LookupResponse[] = [];
  protected form = {
    serverId: '',
    amount: '',
    rateMode: 'M_PER_PLN' as GoldRateMode,
    rate: '',
    description: '',
    contact: '',
  };
  protected busy = false;
  protected error = '';
  protected enabled = false;
  protected get editing() {
    return this.id !== null;
  }
  protected get preview() {
    const amount = parseGoldMillions(this.form.amount);
    return amount === null ? 'Wpisz dodatnią ilość w m' : formatGold(amount);
  }
  protected get total() {
    const amount = parseGoldMillions(this.form.amount);
    const quote = parseGoldRate(this.form.rate, this.form.rateMode);
    return amount === null || quote === null
      ? null
      : goldPackageTotal(amount, quote.rateGoldUnits, quote.ratePriceMinor);
  }
  protected get totalLabel() {
    const total = this.total;
    return total === null
      ? 'Podaj ilość i kurs. Cena pakietu musi wynosić 0,01–1 000 000 PLN.'
      : formatPln(total);
  }
  protected worldChanged() {
    const world = this.worlds.find((w) => String(w.id) === this.form.serverId);
    const mode = defaultGoldRateMode(world?.name || '');
    if (mode !== this.form.rateMode) {
      this.form.rateMode = mode;
      this.form.rate = '';
    }
  }
  constructor() {
    inject(DictionaryService)
      .getAll()
      .subscribe((d) => (this.worlds = d.servers));
    this.service.goldConfig().subscribe({
      next: (c) => (this.enabled = c.enabled),
      error: () => (this.error = 'Nie udało się sprawdzić dostępności wystawiania.'),
    });
    if (this.id)
      this.service.getOne(this.id).subscribe({
        next: (l) => {
          if (l.listingKind !== 'GOLD' || l.status !== 'active') {
            this.error = 'Tej oferty nie można edytować.';
            return;
          }
          const mode = l.goldRateMode === 'M_PER_PLN' ? 'M_PER_PLN' : 'PLN_PER_G';
          this.form = {
            serverId: String(l.server.id),
            amount: goldMillionsInput(l.goldAmount),
            rateMode: mode,
            rate:
              l.goldRateMode === 'M_PER_PLN' && l.rateGoldUnits !== null
                ? goldMillionsInput(l.rateGoldUnits)
                : l.goldRateMode === 'PLN_PER_G' && l.ratePriceMinor !== null
                  ? plnInput(l.ratePriceMinor)
                  : '',
            description: l.description || '',
            contact: l.contact || '',
          };
          if (l.goldRateMode === 'PACKAGE')
            this.error = 'Ta oferta ma starszą cenę pakietu. Podaj kurs przed zapisaniem zmian.';
        },
        error: () => (this.error = 'Nie udało się pobrać oferty.'),
      });
  }
  protected submit() {
    if (this.busy || (!this.enabled && !this.editing)) return;
    const goldAmount = parseGoldMillions(this.form.amount);
    const quote = parseGoldRate(this.form.rate, this.form.rateMode);
    if (goldAmount === null || quote === null || this.total === null || !this.form.serverId) {
      this.error =
        'Wybierz świat i podaj poprawną ilość oraz kurs. Cena pakietu musi wynosić 0,01–1 000 000 PLN.';
      return;
    }
    this.busy = true;
    this.error = '';
    const request = {
      serverId: Number(this.form.serverId),
      goldAmount,
      rateMode: this.form.rateMode,
      ...quote,
      description: this.form.description,
      contact: this.form.contact,
    };
    const save = this.id
      ? this.service.updateGold(this.id, request)
      : this.service.createGold(request);
    save.subscribe({
      next: () => this.router.navigateByUrl('/dashboard'),
      error: (e) => {
        this.busy = false;
        this.error = e.error?.message || 'Nie udało się zapisać oferty.';
      },
    });
  }
}
