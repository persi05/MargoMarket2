import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';

import { SellerReview, SellerReviewsResponse } from '../../core/models/api.models';
import { ReviewService } from '../../core/services/review.service';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { StarRatingComponent } from '../../shared/star-rating/star-rating.component';

/** Polish noun form for a review count: 1 opinia, 2–4 opinie, 5+ opinii (12–14 opinii). */
export function reviewNoun(count: number): string {
  if (count === 1) return 'opinia';
  const units = count % 10, tens = count % 100;
  return units >= 2 && units <= 4 && (tens < 12 || tens > 14) ? 'opinie' : 'opinii';
}

@Component({
  selector: 'mm-seller-reviews',
  standalone: true,
  imports: [DatePipe, RouterLink, PaginationComponent, StarRatingComponent],
  templateUrl: './seller-reviews.component.html',
  styleUrl: './seller-reviews.component.css'
})
export class SellerReviewsComponent {
  readonly sellerId = input.required<number>();
  readonly admin = input(false);
  private readonly service = inject(ReviewService);
  private readonly destroy = inject(DestroyRef);
  private generation = 0;
  protected readonly data = signal<SellerReviewsResponse | null>(null);
  protected readonly loading = signal(false);
  protected readonly error = signal('');
  /** The list is folded by default so many reviews do not push the seller's listings down. */
  protected readonly expanded = signal(false);
  protected hiding: SellerReview | null = null;
  protected hideReason = '';
  protected hideError = '';
  protected hideBusy = false;
  protected readonly levels = [5, 4, 3, 2, 1];
  protected readonly noun = reviewNoun;

  constructor() {
    effect(() => {
      const id = this.sellerId();
      untracked(() => { this.expanded.set(false); this.load(id, 1); });
    });
  }

  protected load(sellerId: number, page: number): void {
    const generation = ++this.generation;
    this.loading.set(true);
    this.error.set('');
    this.service.forSeller(sellerId, page).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: data => { if (generation === this.generation) { this.data.set(data); this.loading.set(false); } },
      error: () => { if (generation === this.generation) { this.error.set('Nie udało się pobrać opinii.'); this.loading.set(false); } }
    });
  }

  protected toggle(): void { this.expanded.update(open => !open); }

  protected average(value: number | null): string {
    return value === null ? '–' : value.toFixed(1).replace('.', ',');
  }

  protected share(level: number): number {
    const summary = this.data()?.summary;
    return summary?.count ? Math.round(summary.distribution[level - 1] * 100 / summary.count) : 0;
  }

  protected startHide(review: SellerReview): void { this.hiding = review; this.hideReason = ''; this.hideError = ''; }

  protected reason(event: Event): void { this.hideReason = (event.target as HTMLInputElement).value; }

  protected confirmHide(): void {
    const review = this.hiding;
    if (!review || this.hideBusy) return;
    if (!this.hideReason.trim()) { this.hideError = 'Podaj uzasadnienie.'; return; }
    this.hideBusy = true;
    this.service.hide(review.id, this.hideReason.trim()).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.hideBusy = false; this.hiding = null; this.load(this.sellerId(), this.data()?.reviews.page ?? 1); },
      error: (e: HttpErrorResponse) => { this.hideBusy = false; this.hideError = e.error?.message ?? 'Nie udało się ukryć opinii.'; }
    });
  }
}
