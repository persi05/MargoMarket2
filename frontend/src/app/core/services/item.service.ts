import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { ItemResponse } from '../models/api.models';

export interface ItemSearchParams {
  search?: string;
  itemTypeId?: number | string | null;
  minLevel?: number | string | null;
  maxLevel?: number | string | null;
  limit?: number;
}

@Injectable({ providedIn: 'root' })
export class ItemService {
  private readonly http = inject(HttpClient);
  private readonly baseUrl = `${environment.apiUrl}/items`;

  search(filters: string | ItemSearchParams): Observable<ItemResponse[]> {
    const normalizedFilters: ItemSearchParams = typeof filters === 'string' ? { search: filters } : filters;
    let params = new HttpParams()
      .set('search', normalizedFilters.search?.trim() || '')
      .set('limit', String(normalizedFilters.limit || 60));

    if (normalizedFilters.itemTypeId) {
      params = params.set('itemTypeId', String(normalizedFilters.itemTypeId));
    }

    if (normalizedFilters.minLevel) {
      params = params.set('minLevel', String(normalizedFilters.minLevel));
    }

    if (normalizedFilters.maxLevel) {
      params = params.set('maxLevel', String(normalizedFilters.maxLevel));
    }

    return this.http.get<ItemResponse[]>(this.baseUrl, { params });
  }
}
