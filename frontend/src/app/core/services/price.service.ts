import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay, tap } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PageResponse, PriceHistoryEntry, PriceOverview, PricingSettings, WorldRates, WorldRatesRequest } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class PriceService {
  private readonly http = inject(HttpClient);
  private readonly pricesUrl = `${environment.apiUrl}/prices/items`;
  private readonly worldsUrl = `${environment.apiUrl}/admin/worlds`;

  // Previews ask for the same item repeatedly; reuse answers for a minute.
  private readonly overviewCache = new Map<string, { at: number; value: Observable<PriceOverview> }>();

  overview(itemId: number, serverId: number, bound = false): Observable<PriceOverview> {
    const key = `${itemId}:${serverId}:${bound}`;
    const cached = this.overviewCache.get(key);
    if (cached && Date.now() - cached.at < 60_000) return cached.value;
    const value = this.http.get<PriceOverview>(`${this.pricesUrl}/${itemId}`, {
      params: new HttpParams().set('serverId', serverId).set('bound', bound)
    }).pipe(
      tap({ error: () => this.overviewCache.delete(key) }),
      shareReplay({ bufferSize: 1, refCount: false })
    );
    this.overviewCache.set(key, { at: Date.now(), value });
    return value;
  }

  /** Drops cached suggestions, e.g. after rates change. */
  clearCache(): void {
    this.overviewCache.clear();
  }

  history(itemId: number, serverId: number | null, page = 1, size = 20): Observable<PageResponse<PriceHistoryEntry>> {
    let params = new HttpParams().set('page', page).set('size', size);
    if (serverId !== null) params = params.set('serverId', serverId);
    return this.http.get<PageResponse<PriceHistoryEntry>>(`${this.pricesUrl}/${itemId}/history`, { params });
  }

  worlds(): Observable<WorldRates[]> {
    return this.http.get<WorldRates[]>(this.worldsUrl);
  }

  settings(): Observable<PricingSettings> {
    return this.http.get<PricingSettings>(`${this.worldsUrl}/settings`);
  }

  saveSettings(settings: PricingSettings): Observable<PricingSettings> {
    this.clearCache();
    return this.http.put<PricingSettings>(`${this.worldsUrl}/settings`, settings);
  }

  saveWorld(request: WorldRatesRequest, id?: number): Observable<WorldRates> {
    this.clearCache();
    return id === undefined
      ? this.http.post<WorldRates>(this.worldsUrl, request)
      : this.http.put<WorldRates>(`${this.worldsUrl}/${id}`, request);
  }
}
