import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { NotificationResponse, PageResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';
import { PaginationComponent } from '../../shared/pagination/pagination.component';

@Component({
  selector: 'mm-notifications-page',
  standalone: true,
  imports: [DatePipe, PaginationComponent],
  templateUrl: './notifications-page.component.html',
  styleUrl: './notifications-page.component.css'
})
export class NotificationsPageComponent {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected notifications: NotificationResponse[] = [];
  protected pageData: PageResponse<NotificationResponse> | null = null;
  protected currentPage = 1;
  protected unreadCount = 0;
  protected loading = true;
  protected notice = '';

  constructor() {
    this.loadNotifications();
  }

  protected markAsRead(notification: NotificationResponse): void {
    if (notification.read) {
      return;
    }

    this.notificationService.markAsRead(notification.id).subscribe({
      next: (updated) => {
        this.notifications = this.notifications.map((item) => (item.id === updated.id ? updated : item));
        if (updated.read) this.unreadCount = Math.max(0, this.unreadCount - 1);
      },
      error: (error: HttpErrorResponse) => {
        this.notice = this.errorMessage(error, 'Nie udało się oznaczyć powiadomienia jako przeczytanego.');
      }
    });
  }

  protected changePage(page: number): void {
    if (this.loading || page < 1 || page === this.currentPage) return;
    this.currentPage = page;
    this.loadNotifications();
  }

  private loadNotifications(): void {
    this.loading = true;
    this.notice = '';

    this.notificationService.unreadCount().subscribe({
      next: result => this.unreadCount = result.count
    });

    this.notificationService.mine(this.currentPage)
      .pipe(finalize(() => {
        this.loading = false;
      }))
      .subscribe({
        next: (page) => {
          this.pageData = page;
          this.notifications = page.content;
        },
        error: (error: HttpErrorResponse) => {
          this.notice = this.errorMessage(error, 'Nie udało się pobrać powiadomień.');
        }
      });
  }

  private errorMessage(error: HttpErrorResponse, fallback: string): string {
    if (error.status === 401 || error.status === 403) {
      this.authService.logout();
      void this.router.navigate(['/auth']);
      return 'Sesja wygasła. Zaloguj się ponownie.';
    }

    return error.error?.message ?? fallback;
  }
}
