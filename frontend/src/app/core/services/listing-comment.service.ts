import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';
import { CommentSendResponse, CommentPostingStatus, ListingCommentResponse, PageResponse } from '../models/api.models';

@Injectable({ providedIn: 'root' })
export class ListingCommentService {
  private readonly http = inject(HttpClient);

  postingStatus(listingId: number): Observable<CommentPostingStatus> {
    return this.http.get<CommentPostingStatus>(`${environment.apiUrl}/listings/${listingId}/comments/posting-status`);
  }

  getComments(listingId: number, page = 1): Observable<PageResponse<ListingCommentResponse>> {
    return this.http.get<PageResponse<ListingCommentResponse>>(
      `${environment.apiUrl}/listings/${listingId}/comments`, { params: { page } }
    );
  }

  addComment(listingId: number, body: string): Observable<CommentSendResponse> {
    return this.http.post<CommentSendResponse>(
      `${environment.apiUrl}/listings/${listingId}/comments`, { body }
    );
  }

  deleteComment(listingId: number, commentId: number): Observable<void> {
    return this.http.delete<void>(`${environment.apiUrl}/listings/${listingId}/comments/${commentId}`);
  }
}
