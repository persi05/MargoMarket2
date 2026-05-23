import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { finalize, forkJoin } from 'rxjs';

import { ListingResponse } from '../../core/models/api.models';
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
  protected activeTab: 'mine' | 'favorites' = 'mine';
  protected loading = true;
  protected notice = '';

  constructor() {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.notice = '';

    forkJoin({
      mine: this.listingService.mine(),
      favorites: this.listingService.favorites()
    }).pipe(finalize(() => {
      this.loading = false;
    })).subscribe({
      next: ({ mine, favorites }) => {
        this.mine = mine;
        this.favorites = favorites;
      },
      error: (response: unknown) => {
        this.handleLoadError(response);
      }
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
        this.favorites = this.favorites.filter((item) => item.id !== listing.id);
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
