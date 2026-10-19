import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ChatMessageResponse, ConversationReportReason, ConversationReportResponse, ConversationResponse, PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ConversationService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/conversations`;

  forListing(listingId: number): Observable<ConversationResponse | null> {
    return this.http.get<ConversationResponse | null>(`${this.baseUrl}/by-listing/${listingId}`);
  }

  inbox(limit = 10, scope: 'ALL' | 'LISTINGS' | 'EXCHANGES' = 'ALL'): Observable<PageResponse<ConversationResponse>> {
    return this.http.get<PageResponse<ConversationResponse>>(`${this.baseUrl}/inbox`, {
      params: new HttpParams().set('limit', limit).set('scope', scope)
    });
  }

  adminChatsPage(page = 1, limit = 10): Observable<PageResponse<ConversationResponse>> {
    return this.http.get<PageResponse<ConversationResponse>>(`${this.baseUrl}/admin/chats/page`, {
      params: new HttpParams().set('page', page).set('limit', limit)
    });
  }

  adminChat(id: number): Observable<ConversationResponse> {
    return this.http.get<ConversationResponse>(`${this.baseUrl}/admin/chats/${id}`);
  }

  adminChatMessages(id: number, page = 1): Observable<PageResponse<ChatMessageResponse>> {
    return this.http.get<PageResponse<ChatMessageResponse>>(`${this.baseUrl}/admin/chats/${id}/messages`, {
      params: new HttpParams().set('page', page)
    });
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

  intermediaryExchanges(page = 1): Observable<PageResponse<ConversationResponse>> {
    return this.http.get<PageResponse<ConversationResponse>>(`${this.baseUrl}/intermediary-exchanges`, {
      params: new HttpParams().set('page', page)
    });
  }

  joinAsIntermediary(id: number): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(`${this.baseUrl}/${id}/join-as-intermediary`, {});
  }

  close(id: number, markSold = false): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(`${this.baseUrl}/${id}/close`, { markSold });
  }

  requestContact(id: number): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(`${this.baseUrl}/${id}/contact-request`, {});
  }

  respondToContact(id: number, accept: boolean): Observable<ConversationResponse> {
    return this.http.post<ConversationResponse>(
      `${this.baseUrl}/${id}/contact-request/${accept ? 'accept' : 'reject'}`, {}
    );
  }

  report(id: number, reason: ConversationReportReason, details: string): Observable<ConversationReportResponse> {
    return this.http.post<ConversationReportResponse>(`${this.baseUrl}/${id}/reports`, { reason, details });
  }

  reports(page = 1): Observable<PageResponse<ConversationReportResponse>> {
    return this.http.get<PageResponse<ConversationReportResponse>>(`${this.baseUrl}/reports`, {
      params: new HttpParams().set('page', page)
    });
  }

  reportMessages(id: number): Observable<ChatMessageResponse[]> {
    return this.http.get<ChatMessageResponse[]>(`${this.baseUrl}/reports/${id}/messages`);
  }

  reviewReport(id: number): Observable<ConversationReportResponse> {
    return this.http.post<ConversationReportResponse>(`${this.baseUrl}/reports/${id}/review`, {});
  }
}
