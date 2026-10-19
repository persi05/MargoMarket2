import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { BlockedWord, BlockedWordRequest, ListingFilter, ListingResponse, PageResponse, UserResponse, UserStats } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/admin`;

  blockedWords(page = 1, search = ''): Observable<PageResponse<BlockedWord>> {
    return this.http.get<PageResponse<BlockedWord>>(`${this.baseUrl}/blocked-words`, {
      params: new HttpParams().set('page', page).set('search', search)
    });
  }

  saveBlockedWord(word: BlockedWordRequest, id?: number): Observable<BlockedWord> {
    return id === undefined
      ? this.http.post<BlockedWord>(`${this.baseUrl}/blocked-words`, word)
      : this.http.put<BlockedWord>(`${this.baseUrl}/blocked-words/${id}`, word);
  }

  deleteBlockedWord(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/blocked-words/${id}`);
  }

  listings(filter: ListingFilter = {}): Observable<PageResponse<ListingResponse>> {
    return this.http.get<PageResponse<ListingResponse>>(`${this.baseUrl}/listings`, {
      params: this.toParams(filter)
    });
  }

  users(page = 1): Observable<PageResponse<UserResponse>> {
    return this.http.get<PageResponse<UserResponse>>(`${this.baseUrl}/users`, {
      params: new HttpParams().set('page', page)
    });
  }

  userStats(id: number): Observable<UserStats> {
    return this.http.get<UserStats>(`${this.baseUrl}/users/${id}/stats`);
  }

  deleteUser(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/users/${id}`);
  }

  private toParams(filter: ListingFilter): HttpParams {
    let params = new HttpParams();

    Object.entries(filter).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });

    return params;
  }
}
