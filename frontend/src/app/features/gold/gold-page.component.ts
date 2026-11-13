import { AuthService } from '../../core/services/auth.service';
import { skip } from 'rxjs';
import { Component, inject, HostListener, DestroyRef } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ListingService } from '../../core/services/listing.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingResponse, LookupResponse, PageResponse } from '../../core/models/api.models';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';
import { DiscussionDrawerComponent } from '../market/discussion-drawer.component';
import { parseGoldMillions, parsePln } from '../../core/utils/gold-format';
@Component({
  selector: 'mm-gold-page',
  standalone: true,
  imports: [FormsModule, RouterLink, ListingCardComponent, DiscussionDrawerComponent],
  templateUrl: './gold-page.component.html',
  styleUrl: './gold-page.component.css',
})
export class GoldPageComponent {
  protected readonly auth = inject(AuthService);
  private service = inject(ListingService);
  private version = 0;
  private destroy = inject(DestroyRef);
  protected favoriteIds = new Set<number>();
  protected favoriteBusy = new Set<number>();
  protected worlds: LookupResponse[] = [];
  protected result: PageResponse<ListingResponse> | null = null;
  protected loading = false;
  protected error = '';
  protected enabled = false;
  protected discussion: ListingResponse | null = null;
  protected filters = {
    serverId: '',
    search: '',
    minAmount: '',
    maxAmount: '',
    minPrice: '',
    maxPrice: '',
    sort: 'newest',
  };
  protected currentPage = 1;
  constructor() {
    const state = this.service.marketState['GOLD'];
    if (state) {
      Object.assign(this.filters, state.filter);
      this.currentPage = state.page;
    }
    inject(DictionaryService)
      .getAll()
      .subscribe((d) => (this.worlds = d.servers));
    this.service.goldConfig().subscribe((c) => (this.enabled = c.enabled));
    this.load(this.currentPage);
    const authSub = inject(AuthService)
      .user$.pipe(skip(1))
      .subscribe(() => {
        this.result = null;
        this.load(this.currentPage);
      });
    this.destroy.onDestroy(() => authSub.unsubscribe());
    const timer = setInterval(() => {
      if (!document.hidden) this.load(this.currentPage);
    }, 30000);
    this.destroy.onDestroy(() => {
      clearInterval(timer);
      this.version++;
    });
  }
  @HostListener('document:visibilitychange') visible() {
    if (!document.hidden) this.load(this.currentPage);
  }
  protected load(page = 1) {
    const f = this.filters;
    if (!this.auth.isLoggedIn) {
      f.minPrice = '';
      f.maxPrice = '';
      if (f.sort.startsWith('price')) f.sort = 'newest';
    }
    const filter: Record<string, unknown> = { ...f, page };
    for (const k of ['minAmount', 'maxAmount', 'minPrice', 'maxPrice'] as const) {
      if (!f[k]) {
        delete filter[k];
        continue;
      }
      const value = k.includes('Amount') ? parseGoldMillions(f[k]) : parsePln(f[k]);
      if (value === null) {
        this.error = 'Nieprawidłowy zakres ilości lub ceny.';
        return;
      }
      filter[k] = value;
    }
    this.currentPage = page;
    this.service.marketState['GOLD'] = { filter: { ...f }, page };
    const version = ++this.version;
    this.loading = true;
    this.error = '';
    this.service.searchGold(filter).subscribe({
      next: (r) => {
        if (version !== this.version) return;
        this.result = r;
        this.loading = false;
        if (this.auth.isLoggedIn && r.content.length)
          this.service.favoriteIds(r.content.map((l) => l.id)).subscribe({
            next: (ids) => {
              if (version === this.version) this.favoriteIds = new Set(ids);
            },
            error: () => {},
          });
      },
      error: () => {
        if (version !== this.version) return;
        this.result = null;
        this.loading = false;
        this.error = 'Nie udało się pobrać ofert złota. Spróbuj ponownie.';
      },
    });
  }
  protected toggleFavorite(l: ListingResponse) {
    if (!this.auth.isLoggedIn || this.favoriteBusy.has(l.id)) return;
    this.favoriteBusy.add(l.id);
    const remove = this.favoriteIds.has(l.id);
    (remove ? this.service.removeFavorite(l.id) : this.service.addFavorite(l.id)).subscribe({
      next: () => {
        remove ? this.favoriteIds.delete(l.id) : this.favoriteIds.add(l.id);
        this.favoriteBusy.delete(l.id);
      },
      error: () => {
        this.favoriteBusy.delete(l.id);
        this.error = 'Nie udało się zmienić obserwowanych.';
      },
    });
  }
  protected refresh() {
    this.service.invalidate();
    this.load(this.currentPage);
  }
}
