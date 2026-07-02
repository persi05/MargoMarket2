import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { BehaviorSubject, combineLatest, finalize, forkJoin, of, shareReplay, switchMap, tap } from 'rxjs';

import { ListingResponse, PageResponse, UserResponse, UserStats } from '../../core/models/api.models';
import { AdminService } from '../../core/services/admin.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';

@Component({
  selector: 'mm-admin-page',
  standalone: true,
  imports: [AsyncPipe, DatePipe, DecimalPipe, ReactiveFormsModule, ListingCardComponent],
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.css'
})
export class AdminPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly adminService = inject(AdminService);
  private readonly listingService = inject(ListingService);
  protected readonly dictionaries$ = inject(DictionaryService).getAll();
  private readonly pageSubject = new BehaviorSubject(1);
  private readonly usersReloadSubject = new BehaviorSubject(0);

    protected activeTab: 'listings' | 'users' = 'listings';
  protected loadingListings = false;
  protected loadingUsers = false;
  protected notice = '';

  protected readonly filters = this.fb.nonNullable.group({
    search: [''],
    serverId: [''],
    status: ['']
  });

  protected readonly listings$ = combineLatest([this.pageSubject]).pipe(
    tap(() => {
      this.loadingListings = true;
      this.notice = '';
    }),
    switchMap(([page]) => this.adminService.listings({ ...this.filters.getRawValue(), page }).pipe(
      finalize(() => {
        this.loadingListings = false;
      })
    )),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  protected readonly users$ = this.usersReloadSubject.pipe(
    tap(() => {
      this.loadingUsers = true;
      this.notice = '';
    }),
    switchMap(() => this.adminService.users().pipe(
      switchMap((users) => {
        if (!users.length) {
          return of([]);
        }

        return forkJoin(users.map((user) =>
          this.adminService.userStats(user.id).pipe(
            switchMap((stats) => of({ user, stats }))
          )
        ));
      }),
      finalize(() => {
        this.loadingUsers = false;
      })
    )),
    shareReplay({ bufferSize: 1, refCount: true })
  );

  applyFilters(): void {
    this.pageSubject.next(1);
  }

  resetFilters(): void {
    this.filters.reset();
    this.applyFilters();
  }

  nextPage(page: PageResponse<ListingResponse>): void {
    if (!page.last) {
      this.pageSubject.next(page.page + 1);
    }
  }

  previousPage(page: PageResponse<ListingResponse>): void {
    if (!page.first) {
      this.pageSubject.next(page.page - 1);
    }
  }

  markAsSold(listing: ListingResponse): void {
    this.listingService.markAsSold(listing.id).subscribe({
      next: () => {
        this.notice = `${listing.itemName} oznaczono jako sprzedane.`;
        this.pageSubject.next(this.pageSubject.value);
      },
      error: () => {
        this.notice = 'Nie udało się oznaczyć ogłoszenia jako sprzedane.';
      }
    });
  }

  deleteListing(listing: ListingResponse): void {
    if (!confirm(`Usunąć ogłoszenie "${listing.itemName}"?`)) {
      return;
    }

    this.listingService.delete(listing.id).subscribe({
      next: () => {
        this.notice = `${listing.itemName} usunięto.`;
        this.pageSubject.next(this.pageSubject.value);
      },
      error: () => {
        this.notice = 'Nie udało się usunąć ogłoszenia.';
      }
    });
  }

  deleteUser(user: UserResponse): void {
    if (!confirm(`Usunąć użytkownika ${user.email}?`)) {
      return;
    }

    this.adminService.deleteUser(user.id).subscribe({
      next: () => {
        this.notice = `${user.email} usunięto.`;
        this.usersReloadSubject.next(this.usersReloadSubject.value + 1);
      },
      error: () => {
        this.notice = 'Nie udało się usunąć użytkownika.';
      }
    });
  }

  protected roleLabel(role: string): string {
    if (role === 'admin') {
      return 'Administrator';
    }

    if (role === 'user') {
      return 'Użytkownik';
    }

    return role;
  }
}

interface UserWithStats {
  user: UserResponse;
  stats: UserStats;
}
