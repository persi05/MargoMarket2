import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, of, switchMap, map, tap, shareReplay, finalize } from 'rxjs';
import { AuthService } from './auth.service';

import { environment } from '../../../environments/environment';
import { ListingFilter, ListingRequest, ListingResponse, PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ListingService {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private generation = 0;
  private cache = new Map<string, { expires: number; value: PageResponse<ListingResponse> }>();
  private pending = new Map<string, Observable<PageResponse<ListingResponse>>>();
  readonly marketState: Record<string, { filter: Record<string, unknown>; page: number }> = {};
  invalidate(): void {
    this.generation++;
    this.cache.clear();
    this.pending.clear();
  }
  private cached(
    url: string,
    filter: Record<string, unknown>,
  ): Observable<PageResponse<ListingResponse>> {
    const entries = Object.entries(filter)
      .filter(([, v]) => v !== undefined && v !== null && v !== '')
      .sort(([a], [b]) => a.localeCompare(b));
    const key = url + JSON.stringify(entries);
    const found = this.cache.get(key);
    const generation = this.generation;
    let publicPage$: Observable<PageResponse<ListingResponse>>;
    if (found && found.expires > Date.now()) publicPage$ = of(found.value);
    else {
      const existing = this.pending.get(key);
      if (existing) publicPage$ = existing;
      else {
        publicPage$ = this.http
          .get<PageResponse<ListingResponse>>(url, {
            params: this.toParams({ ...Object.fromEntries(entries), publicOnly: true }),
          })
          .pipe(
            map((page) => ({
              ...page,
              content: page.content.map((listing): ListingResponse => ({
                ...listing,
                price: null,
                priceMinor: null,
                rateGoldUnits: null,
                ratePriceMinor: null,
                contact: null,
              })),
            })),
            tap((value) => {
              if (generation !== this.generation) return;
              if (this.cache.size >= 20) this.cache.delete(this.cache.keys().next().value!);
              this.cache.set(key, { expires: Date.now() + 30000, value });
            }),
            finalize(() => {
              if (generation === this.generation) this.pending.delete(key);
            }),
            shareReplay({ bufferSize: 1, refCount: true }),
          );
        this.pending.set(key, publicPage$);
      }
    }
    return publicPage$.pipe(
      switchMap((page) => {
        if (!this.auth.isLoggedIn || !page.content.length) return of(page);
        return this.http
          .get<
            Array<{
              id: number;
              price: number | null;
              priceMinor: number | null;
              goldAmount: number | null;
              goldRateMode: ListingResponse['goldRateMode'];
              rateGoldUnits: number | null;
              ratePriceMinor: number | null;
              contact: string;
              status: string;
            }>
          >(this.baseUrl + '/private-details', {
            params: { ids: page.content.map((l) => l.id).join(',') },
          })
          .pipe(
            map((details) => {
              const byId = new Map(details.map((d) => [d.id, d]));
              return {
                ...page,
                content: page.content
                  .filter((l) => byId.get(l.id)?.status === 'active')
                  .map((l): ListingResponse => {
                    const d = byId.get(l.id)!;
                    return l.listingKind === 'GOLD'
                      ? {
                          ...l,
                          priceMinor: d.priceMinor,
                          goldAmount: d.goldAmount ?? l.goldAmount,
                          goldRateMode: d.goldRateMode ?? l.goldRateMode,
                          rateGoldUnits: d.rateGoldUnits,
                          ratePriceMinor: d.ratePriceMinor,
                          contact: d.contact,
                          status: d.status,
                        }
                      : { ...l, price: d.price, contact: d.contact, status: d.status };
                  }),
              };
            }),
          );
      }),
    );
  }
  searchGold(filter: Record<string, unknown> = {}): Observable<PageResponse<ListingResponse>> {
    return this.cached(this.baseUrl + '/gold', filter);
  }
  goldConfig(): Observable<{ enabled: boolean }> {
    return this.http.get<{ enabled: boolean }>(this.baseUrl + '/gold/config');
  }
  createGold(request: unknown): Observable<ListingResponse> {
    return this.http
      .post<ListingResponse>(this.baseUrl + '/gold', request)
      .pipe(tap(() => this.invalidate()));
  }
  updateGold(id: number, request: unknown): Observable<ListingResponse> {
    return this.http
      .put<ListingResponse>(this.baseUrl + '/' + id + '/gold', request)
      .pipe(tap(() => this.invalidate()));
  }
  withdraw(id: number): Observable<ListingResponse> {
    return this.http
      .post<ListingResponse>(this.baseUrl + '/' + id + '/withdraw', {})
      .pipe(tap(() => this.invalidate()));
  }

  private readonly baseUrl = `${environment.apiUrl}/listings`;

  search(filter: ListingFilter = {}): Observable<PageResponse<ListingResponse>> {
    return this.cached(this.baseUrl, { ...filter });
  }

  getOne(id: number): Observable<ListingResponse> {
    return this.http.get<ListingResponse>(`${this.baseUrl}/${id}`);
  }

  create(request: ListingRequest): Observable<ListingResponse> {
    return this.http
      .post<ListingResponse>(this.baseUrl, request)
      .pipe(tap(() => this.invalidate()));
  }

  mine(page = 1): Observable<PageResponse<ListingResponse>> {
    return this.http.get<PageResponse<ListingResponse>>(`${this.baseUrl}/mine`, {
      params: new HttpParams().set('page', page),
    });
  }

  favorites(page = 1): Observable<PageResponse<ListingResponse>> {
    return this.http.get<PageResponse<ListingResponse>>(`${this.baseUrl}/favorites`, {
      params: new HttpParams().set('page', page),
    });
  }

  favoriteIds(listingIds: number[]): Observable<number[]> {
    const params = listingIds.reduce(
      (result, id) => result.append('listingIds', id),
      new HttpParams(),
    );
    return this.http.get<number[]>(`${this.baseUrl}/favorites/ids`, { params });
  }

  addFavorite(id: number): Observable<void> {
    return this.http.post<void>(`${this.baseUrl}/${id}/favorite`, {});
  }

  removeFavorite(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}/favorite`);
  }

  markAsSold(id: number): Observable<ListingResponse> {
    return this.http
      .post<ListingResponse>(`${this.baseUrl}/${id}/sold`, {})
      .pipe(tap(() => this.invalidate()));
  }

  delete(id: number): Observable<void> {
    return this.http.delete<void>(`${this.baseUrl}/${id}`).pipe(tap(() => this.invalidate()));
  }

  private toParams(filter: Record<string, unknown>): HttpParams {
    let params = new HttpParams();

    Object.entries(filter).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        params = params.set(key, String(value));
      }
    });

    return params;
  }
}
