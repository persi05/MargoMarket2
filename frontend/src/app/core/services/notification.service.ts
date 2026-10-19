import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { NotificationResponse, PageResponse, UnreadNotificationsResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class NotificationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/notifications`;

  mine(page = 1): Observable<PageResponse<NotificationResponse>> {
    return this.http.get<PageResponse<NotificationResponse>>(this.baseUrl, {
      params: new HttpParams().set('page', page)
    });
  }

  unreadCount(): Observable<UnreadNotificationsResponse> {
    return this.http.get<UnreadNotificationsResponse>(`${this.baseUrl}/unread-count`);
  }

  markAsRead(id: number): Observable<NotificationResponse> {
    return this.http.post<NotificationResponse>(`${this.baseUrl}/${id}/read`, {});
  }
}
