import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { PendingReview, SellerReview, SellerReviewsResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  private readonly http = inject(HttpClient);
  private readonly api = environment.apiUrl;

  forSeller(sellerId: number, page = 1): Observable<SellerReviewsResponse> {
    return this.http.get<SellerReviewsResponse>(`${this.api}/profiles/${sellerId}/reviews`, {
      params: new HttpParams().set('page', page)
    });
  }

  pending(): Observable<PendingReview[]> {
    return this.http.get<PendingReview[]>(`${this.api}/reviews/pending`);
  }

  create(confirmationId: number, rating: number, comment: string): Observable<SellerReview> {
    return this.http.post<SellerReview>(`${this.api}/reviews`, { confirmationId, rating, comment: comment.trim() || null });
  }

  hide(id: number, reason: string): Observable<void> {
    return this.http.post<void>(`${this.api}/admin/reviews/${id}/hide`, { reason });
  }
}
