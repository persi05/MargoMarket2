import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, shareReplay } from 'rxjs';

import { environment } from '../../../environments/environment';
import { DictionariesResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class DictionaryService {
  private readonly http = inject(HttpClient);
  private readonly dictionaries$ = this.http
    .get<DictionariesResponse>(`${environment.apiUrl}/dictionaries`)
    .pipe(shareReplay(1));

  getAll(): Observable<DictionariesResponse> {
    return this.dictionaries$;
  }
}
