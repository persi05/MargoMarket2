import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { PendingReview } from '../../core/models/api.models';
import { ReviewService } from '../../core/services/review.service';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { RATING_LABELS, StarRatingComponent } from '../../shared/star-rating/star-rating.component';

interface Draft { rating: number; comment: string; busy: boolean; error: string; }

@Component({
  selector: 'mm-reviews-page',
  standalone: true,
  imports: [DatePipe, RouterLink, PageHeaderComponent, StarRatingComponent],
  templateUrl: './reviews-page.component.html',
  styleUrl: './reviews-page.component.css'
})
export class ReviewsPageComponent {
  private readonly reviews = inject(ReviewService);
  private readonly destroy = inject(DestroyRef);
  protected readonly maxComment = 1000;
  protected readonly labels = RATING_LABELS;
  protected pending: PendingReview[] = [];
  protected drafts = new Map<number, Draft>();
  protected loading = true;
  protected error = '';
  protected notice = '';

  constructor() {
    this.reviews.pending().pipe(takeUntilDestroyed()).subscribe({
      next: pending => {
        this.pending = pending;
        pending.forEach(p => this.drafts.set(p.confirmationId, { rating: 0, comment: '', busy: false, error: '' }));
        this.loading = false;
      },
      error: () => { this.error = 'Nie udało się pobrać wymian do oceny.'; this.loading = false; }
    });
  }

  protected draft(p: PendingReview): Draft { return this.drafts.get(p.confirmationId)!; }

  protected comment(p: PendingReview, event: Event): void {
    this.draft(p).comment = (event.target as HTMLTextAreaElement).value;
  }

  protected submit(p: PendingReview): void {
    const draft = this.draft(p);
    if (draft.busy) return;
    if (!draft.rating) { draft.error = 'Wybierz ocenę od 1 do 5.'; return; }
    draft.busy = true;
    draft.error = '';
    this.reviews.create(p.confirmationId, draft.rating, draft.comment).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.pending = this.pending.filter(x => x.confirmationId !== p.confirmationId);
        this.drafts.delete(p.confirmationId);
        this.notice = `Dziękujemy! Opinia o sprzedawcy ${p.sellerDisplayName} została opublikowana.`;
      },
      error: (e: HttpErrorResponse) => { draft.busy = false; draft.error = e.error?.message ?? 'Nie udało się zapisać opinii.'; }
    });
  }
}
