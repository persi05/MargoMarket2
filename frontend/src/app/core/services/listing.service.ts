import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ListingFilter, ListingRequest, ListingResponse, PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ListingService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/listings`;

  search(filter: ListingFilter = {}): Observable<PageResponse<ListingResponse>> {
    return this.http.get<PageResponse<ListingResponse>>(this.baseUrl, {
      params: this.toParams(filter)
    });
  }

  create(request: ListingRequest): Observable<ListingResponse> {
    return this.http.post<ListingResponse>(this.baseUrl, request);
  }

  mine(): Observable<ListingResponse[]> {
    return this.http.get<ListingResponse[]>(`${this.baseUrl}/mine`);
  }

  favorites(): Observable<ListingResponse[]> {
    return this.http.get<ListingResponse[]>(`${this.baseUrl}/favorites`);
  }

  addFavorite(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/favorite`, {});
  }

  removeFavorite(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/favorite`);
  }

  markAsSold(id: number): Observable<ListingResponse> {
    return this.http.post<ListingResponse>(`${this.baseUrl}/${id}/sold`, {});
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`);
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
