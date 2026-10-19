import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize } from 'rxjs';

import { ListingResponse, PageResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { ListingService } from '../../core/services/listing.service';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';

@Component({
  selector: 'mm-dashboard-page',
  standalone: true,
  imports: [RouterLink, ListingCardComponent],
  templateUrl: './dashboard-page.component.html',
  styleUrl: './dashboard-page.component.css'
})
export class DashboardPageComponent {
  private readonly listingService = inject(ListingService);
  private readonly authService = inject(AuthService);
  private readonly router = inject(Router);

  protected mine: ListingResponse[] = [];
  protected favorites: ListingResponse[] = [];
  protected pageData: PageResponse<ListingResponse> | null = null;
  protected currentPage = 1;
  protected activeTab: 'mine' | 'favorites' = 'mine';
  protected loading = true;
  protected loadingMore = false;
  protected notice = '';

  protected get isFavoritesPage(): boolean {
    return this.activeTab === 'favorites';
  }

  protected get pageTitle(): string {
    if (this.isFavoritesPage) {
      return 'Obserwowane ogłoszenia';
    }

    return 'Moje ogłoszenia';
  }

  constructor() {
    this.activeTab = this.router.url.startsWith('/favorites') ? 'favorites' : 'mine';
    this.reload();
  }

  reload(): void {
    this.currentPage = 1;
    this.loading = true;

    const request = this.isFavoritesPage
      ? this.listingService.favorites(this.currentPage)
      : this.listingService.mine(this.currentPage);
    request.pipe(finalize(() => {
      this.loading = false;
    })).subscribe({
      next: (page) => {
        this.pageData = page;
        if (this.isFavoritesPage) this.favorites = page.content;
        else this.mine = page.content;
      },
      error: (response: unknown) => {
        this.handleLoadError(response);
      }
    });
  }

  protected loadMore(): void {
    if (this.loading || this.loadingMore || !this.pageData || this.pageData.last) return;

    this.loadingMore = true;
    const request = this.isFavoritesPage
      ? this.listingService.favorites(this.pageData.page + 1)
      : this.listingService.mine(this.pageData.page + 1);
    request.pipe(
      finalize(() => this.loadingMore = false)
    ).subscribe({
      next: (page) => {
        const loaded = this.isFavoritesPage ? this.favorites : this.mine;
        const loadedIds = new Set(loaded.map((listing) => listing.id));
        const listings = [...loaded, ...page.content.filter((listing) => !loadedIds.has(listing.id))];
        if (this.isFavoritesPage) this.favorites = listings;
        else this.mine = listings;
        this.currentPage = page.page;
        this.pageData = page;
      },
      error: (response: unknown) => this.handleLoadError(response)
    });
  }

  markAsSold(listing: ListingResponse): void {
    this.listingService.markAsSold(listing.id).subscribe({
      next: () => {
        this.notice = `${listing.itemName} oznaczono jako sprzedane.`;
        this.reload();
      },
      error: () => {
        this.notice = 'Nie udało się oznaczyć ogłoszenia.';
      }
    });
  }

  deleteListing(listing: ListingResponse): void {
    this.listingService.delete(listing.id).subscribe({
      next: () => {
        this.notice = `${listing.itemName} usunięto.`;
        this.reload();
      },
      error: () => {
        this.notice = 'Nie udało się usunąć ogłoszenia.';
      }
    });
  }

  removeFavorite(listing: ListingResponse): void {
    this.listingService.removeFavorite(listing.id).subscribe({
      next: () => {
        this.reload();
      }
    });
  }

  private handleLoadError(response: unknown): void {
    if (response instanceof HttpErrorResponse && (response.status === 401 || response.status === 403)) {
      this.notice = 'Sesja wygasła albo token jest niepoprawny. Zaloguj się ponownie.';
      this.authService.logout();
      void this.router.navigate(['/auth']);
      return;
    }

    this.notice = 'Nie udało się pobrać panelu. Sprawdź, czy backend działa i spróbuj odświeżyć stronę.';
  }

}
