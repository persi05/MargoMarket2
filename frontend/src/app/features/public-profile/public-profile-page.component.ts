import { Component, DestroyRef, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { combineLatest, switchMap, forkJoin, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ListingResponse, PageResponse } from '../../core/models/api.models';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { AuthService } from '../../core/services/auth.service';

interface Profile {
  endedAuctions?: number;
  id: number;
  displayName: string;
  createdAt: string;
  activeListings: number;
  soldListings: number;
  confirmedPurchases: number;
  confirmedSales: number;
}

@Component({
  selector: 'mm-public-profile-page',
  standalone: true,
  imports: [DatePipe, RouterLink, ListingCardComponent, PaginationComponent],
  templateUrl: './public-profile-page.component.html',
  styleUrl: './public-profile-page.component.css'
})
export class PublicProfilePageComponent {
  private readonly http = inject(HttpClient);
  private readonly route = inject(ActivatedRoute);
  private readonly destroy = inject(DestroyRef);
  private generation = 0;
  protected profile: Profile | null = null;
  protected listings: PageResponse<ListingResponse> | null = null;
  protected loading = true;
  protected listingsLoading = false;
  protected error = '';

  constructor() {
    combineLatest([this.route.paramMap, inject(AuthService).user$]).pipe(
      switchMap(([params]) => {
        this.generation++;
        this.loading = true;
        this.listingsLoading = false;
        this.error = '';
        this.profile = null;
        this.listings = null;
        const url = `${environment.apiUrl}/profiles/${params.get('id')}`;
        return forkJoin({
          profile: this.http.get<Profile>(url),
          listings: this.http.get<PageResponse<ListingResponse>>(`${url}/listings`)
        }).pipe(catchError(e => {
          this.error = e.status === 404 ? 'Profil niedostępny.' : 'Nie udało się pobrać profilu. Spróbuj ponownie.';
          return of(null);
        }));
      }),
      takeUntilDestroyed()
    ).subscribe(result => {
      this.loading = false;
      if (result) { this.profile = result.profile; this.listings = result.listings; }
    });
  }

  protected page(page: number): void {
    if (!this.profile || this.listingsLoading) return;
    const generation = ++this.generation;
    this.listingsLoading = true;
    this.error = '';
    this.http.get<PageResponse<ListingResponse>>(`${environment.apiUrl}/profiles/${this.profile.id}/listings`, {
      params: {page}
    }).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: result => {
        if (generation !== this.generation) return;
        this.listings = result;
        this.listingsLoading = false;
      },
      error: () => {
        if (generation !== this.generation) return;
        this.error = 'Nie udało się pobrać ogłoszeń.';
        this.listingsLoading = false;
      }
    });
  }
}
