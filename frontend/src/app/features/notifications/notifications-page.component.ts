import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject } from '@angular/core';
import { Router } from '@angular/router';
import { finalize } from 'rxjs';

import { NotificationResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { NotificationService } from '../../core/services/notification.service';

@Component({
  selector: 'mm-notifications-page',
  standalone: true,
  imports: [DatePipe],
  templateUrl: './notifications-page.component.html',
  styleUrl: './notifications-page.component.css'
})
export class NotificationsPageComponent {
  private readonly authService = inject(AuthService);
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);

  protected notifications: NotificationResponse[] = [];
  protected loading = true;
  protected notice = '';

  constructor() {
    this.loadNotifications();
  }

  protected get unreadCount(): number {
    return this.notifications.filter((notification) => !notification.read).length;
  }

  protected markAsRead(notification: NotificationResponse): void {
    if (notification.read) {
      return;
    }

    this.notificationService.markAsRead(notification.id).subscribe({
      next: (updated) => {
        this.notifications = this.notifications.map((item) => (item.id === updated.id ? updated : item));
      },
      error: (error: HttpErrorResponse) => {
        this.notice = this.errorMessage(error, 'Nie udało się oznaczyć powiadomienia jako przeczytanego.');
      }
    });
  }

  private loadNotifications(): void {
    this.loading = true;
    this.notice = '';

    this.notificationService.mine()
      .pipe(finalize(() => {
        this.loading = false;
      }))
      .subscribe({
        next: (notifications) => {
          this.notifications = notifications;
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
