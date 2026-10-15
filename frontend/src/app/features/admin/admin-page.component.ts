import { AsyncPipe, DatePipe, DecimalPipe } from '@angular/common';
import { Component, inject } from '@angular/core';
import { FormBuilder, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { BehaviorSubject, combineLatest, finalize, forkJoin, of, shareReplay, switchMap, tap } from 'rxjs';

import { ChatMessageResponse, ConversationReportResponse, ConversationResponse, ListingResponse, PageResponse, UserResponse, UserStats } from '../../core/models/api.models';
import { AdminService } from '../../core/services/admin.service';
import { ConversationService } from '../../core/services/conversation.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';
import { MessagesPageComponent } from '../messages/messages-page.component';
import { ModerationPanelComponent } from './moderation-panel.component';

@Component({
  selector: 'mm-admin-page',
  standalone: true,
  imports: [AsyncPipe, DatePipe, DecimalPipe, ReactiveFormsModule, RouterLink, ListingCardComponent, MessagesPageComponent, ModerationPanelComponent],
  templateUrl: './admin-page.component.html',
  styleUrl: './admin-page.component.css'
})
export class AdminPageComponent {
  private readonly fb = inject(FormBuilder);
  private readonly adminService = inject(AdminService);
  private readonly conversationsService = inject(ConversationService);
  private readonly listingService = inject(ListingService);
  protected readonly dictionaries$ = inject(DictionaryService).getAll();
  private readonly pageSubject = new BehaviorSubject(1);
  private readonly usersReloadSubject = new BehaviorSubject(0);

  protected activeTab: 'listings' | 'users' | 'moderation' | 'reports' | 'intermediary' | 'chats' = 'listings';
  protected selectedAdminChatId: number | null = null;
  protected reports: ConversationReportResponse[] = [];
  protected intermediaryRequests: ConversationResponse[] = [];
  protected selectedReportId: number | null = null;
  protected reportMessages: ChatMessageResponse[] = [];
  protected loadingReports = false;
  protected loadingRequests = false;
  protected reportBusy = false;
  protected intermediaryBusy = false;
  protected closingExchangeId: number | null = null;
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

  protected selectTab(tab: typeof this.activeTab): void {
    this.activeTab = tab;
    this.notice = '';
    if (tab === 'chats') this.selectedAdminChatId = null;
    if (tab === 'listings') this.pageSubject.next(this.pageSubject.value);
    if (tab === 'reports') this.loadReports();
    if (tab === 'intermediary') this.loadIntermediaryRequests();
  }

  protected goToReportChat(report: ConversationReportResponse): void {
    this.selectedAdminChatId = report.conversationId;
    this.activeTab = 'chats';
    this.notice = '';
  }

  protected loadReports(): void {
    this.loadingReports = true;
    this.conversationsService.reports().pipe(finalize(() => this.loadingReports = false)).subscribe({
      next: reports => this.reports = reports,
      error: () => this.notice = 'Nie udało się wczytać zgłoszeń.'
    });
  }

  protected loadIntermediaryRequests(): void {
    this.loadingRequests = true;
    this.conversationsService.intermediaryExchanges().pipe(finalize(() => this.loadingRequests = false)).subscribe({
      next: requests => this.intermediaryRequests = requests,
      error: () => this.notice = 'Nie udało się wczytać wymian.'
    });
  }

  protected showReportMessages(report: ConversationReportResponse): void {
    if (this.selectedReportId === report.id) {
      this.selectedReportId = null;
      this.reportMessages = [];
      return;
    }
    this.selectedReportId = report.id;
    this.reportMessages = [];
    this.conversationsService.reportMessages(report.id).subscribe({
      next: messages => { if (this.selectedReportId === report.id) this.reportMessages = messages.reverse(); },
      error: () => this.notice = 'Nie udało się wczytać wiadomości.'
    });
  }

  protected reviewReport(report: ConversationReportResponse): void {
    if (this.reportBusy) return;
    this.reportBusy = true;
    this.conversationsService.reviewReport(report.id).pipe(finalize(() => this.reportBusy = false)).subscribe({
      next: updated => this.reports = this.reports.map(item => item.id === updated.id ? updated : item),
      error: () => this.notice = 'Nie udało się oznaczyć zgłoszenia jako sprawdzone.'
    });
  }

  protected joinAsIntermediary(request: ConversationResponse): void {
    if (this.intermediaryBusy) return;
    this.intermediaryBusy = true;
    this.conversationsService.joinAsIntermediary(request.id)
      .pipe(finalize(() => this.intermediaryBusy = false)).subscribe({
        next: () => {
          this.loadIntermediaryRequests();
          this.notice = 'Dołączono do rozmowy jako pośrednik.';
        },
        error: response => this.notice = response.error?.message || 'Nie udało się dołączyć do rozmowy.'
      });
  }

  protected closeExchange(exchange: ConversationResponse): void {
    if (this.closingExchangeId !== null || exchange.closedAt) return;
    if (!confirm(exchange.listingId
      ? 'Zakończyć tę wymianę i oznaczyć ogłoszenie jako sprzedane?'
      : 'Zakończyć tę wymianę?')) return;
    this.closingExchangeId = exchange.id;
    this.conversationsService.close(exchange.id)
      .pipe(finalize(() => this.closingExchangeId = null)).subscribe({
        next: () => {
          this.loadIntermediaryRequests();
          this.notice = 'Wymiana została zakończona.';
        },
        error: response => this.notice = response.error?.message || 'Nie udało się zakończyć wymiany.'
      });
  }

  protected reasonLabel(reason: string): string {
    switch (reason) {
      case 'SCAM': return 'Podejrzenie scamu';
      case 'FRAUD': return 'Oszustwo';
      default: return reason;
    }
  }

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
    if (!confirm(`Usunąć użytkownika ${user.username}?`)) {
      return;
    }

    this.adminService.deleteUser(user.id).subscribe({
      next: () => {
        this.notice = `${user.username} usunięto.`;
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
