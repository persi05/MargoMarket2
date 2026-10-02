import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ChatMessageResponse, ConversationResponse, PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/conversations`;

  list(): Observable<ConversationResponse[]> {
    return this.http.get<ConversationResponse[]>(this.baseUrl);
  }

  unreadCount(): Observable<{ count: number }> {
    return this.http.get<{ count: number }>(`${this.baseUrl}/unread-count`);
  }

  get(id: number): Observable<ConversationResponse> {
    return this.http.get<ConversationResponse>(`${this.baseUrl}/${id}`);
  }

  messages(id: number, page = 1): Observable<PageResponse<ChatMessageResponse>> {
    return this.http.get<PageResponse<ChatMessageResponse>>(`${this.baseUrl}/${id}/messages`, {
      params: new HttpParams().set('page', page)
    });
  }

  start(listingId: number, body: string): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(this.baseUrl, { listingId, body });
  }

  send(id: number, body: string): Observable<ChatMessageResponse> {
    return this.http.post<ChatMessageResponse>(`${this.baseUrl}/${id}/messages`, { body });
  }

  requestIntermediary(id: number): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(`${this.baseUrl}/${id}/intermediary-request`, {});
  }

  intermediaryRequests(): Observable<ConversationResponse[]> {
    return this.http.get<ConversationResponse[]>(`${this.baseUrl}/intermediary-requests`);
  }

  joinAsIntermediary(id: number): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(`${this.baseUrl}/${id}/join-as-intermediary`, {});
  }
}
