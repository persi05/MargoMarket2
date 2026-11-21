import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse, PriceHistoryEntry, PriceOverview, WorldRates, WorldRatesRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class PriceService {
  private readonly http = inject(HttpClient);
  private readonly pricesUrl = `${environment.apiUrl}/prices/items`;
  private readonly worldsUrl = `${environment.apiUrl}/admin/worlds`;

  overview(itemId: number, serverId: number, bound = false): Observable<PriceOverview> {
    return this.http.get<PriceOverview>(`${this.pricesUrl}/${itemId}`, {
      params: new HttpParams().set('serverId', serverId).set('bound', bound)
    });
  }

  history(itemId: number, serverId: number | null, page = 1, size = 20): Observable<PageResponse<PriceHistoryEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (serverId !== null) params = params.set('serverId', serverId);
    return this.http.get<PageResponse<PriceHistoryEntry>>(`${this.pricesUrl}/${itemId}/history`, { params });
  }

  worlds(): Observable<WorldRates[]> {
    return this.http.get<WorldRates[]>(this.worldsUrl);
  }

  saveWorld(request: WorldRatesRequest, id?: number): Observable<WorldRates> {
    return id === undefined
      ? this.http.post<WorldRates>(this.worldsUrl, request)
      : this.http.put<WorldRates>(`${this.worldsUrl}/${id}`, request);
  }
}
