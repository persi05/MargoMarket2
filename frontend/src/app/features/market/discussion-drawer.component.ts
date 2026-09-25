import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, HostListener, Input, OnChanges, OnDestroy, OnInit, Output, EventEmitter, SimpleChanges, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { ListingCommentResponse, ListingResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { ListingCommentService } from '../../core/services/listing-comment.service';
import { formatListingPrice } from '../../core/utils/price-format';

@Component({
  selector: 'mm-discussion-drawer',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink],
  templateUrl: './discussion-drawer.component.html',
  styleUrl: './discussion-drawer.component.css'
})
export class DiscussionDrawerComponent implements OnChanges, OnInit, OnDestroy {
  @Input({ required: true }) listing!: ListingResponse;
  @Output() closed = new EventEmitter<void>();
  @ViewChild('messages') private messages?: ElementRef<HTMLElement>;
  @ViewChild('closeButton') private closeButton?: ElementRef<HTMLButtonElement>;

  protected readonly authService = inject(AuthService);
  private readonly commentService = inject(ListingCommentService);
  private readonly destroyRef = inject(DestroyRef);
  private refreshTimer?: ReturnType<typeof setInterval>;

  protected comments: ListingCommentResponse[] = [];
  protected draft = '';
  protected loading = false;
  protected loadingOlder = false;
  protected sending = false;
  protected deletingIds = new Set<number>();
  protected hasMore = false;
  protected loadError = '';
  protected sendError = '';
  protected currentUserId: number | null = null;
  private nextOlderPage = 2;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['listing'] && this.listing) {
      this.loadInitial();
    }
  }

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((user) => this.currentUserId = user?.id ?? null);
    this.refreshTimer = setInterval(() => this.refreshLatest(), 12000);
    setTimeout(() => this.closeButton?.nativeElement.focus());
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.closed.emit();
  }

  protected get priceLabel(): string {
    return formatListingPrice(this.listing.price, this.listing.currency.name);
  }

  protected loadOlder(): void {
    if (this.loadingOlder || !this.hasMore) {
      return;
    }
    const listingId = this.listing.id;
    this.loadingOlder = true;
    this.loadError = '';
    this.commentService.getComments(listingId, this.nextOlderPage)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loadingOlder = false))
      .subscribe({
        next: (page) => {
          if (listingId !== this.listing.id) return;
          this.mergeComments(page.content);
          this.hasMore = !page.last;
          this.nextOlderPage++;
        },
        error: () => this.loadError = 'Nie udało się wczytać starszych komentarzy.'
      });
  }

  protected send(): void {
    const body = this.draft.trim();
    if (this.sending || !body || body.length > 1000 || this.listing.status !== 'active') {
      return;
    }
    const listingId = this.listing.id;
    this.sending = true;
    this.sendError = '';
    this.commentService.addComment(listingId, body)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.sending = false))
      .subscribe({
        next: (comment) => {
          if (listingId !== this.listing.id) return;
          this.draft = '';
          this.mergeComments([comment]);
          this.scrollToBottom();
        },
        error: () => this.sendError = 'Nie udało się dodać komentarza. Spróbuj ponownie.'
      });
  }

  protected onComposerKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Enter' || event.shiftKey || event.isComposing) return;
    event.preventDefault();
    this.send();
  }

  protected deleteComment(comment: ListingCommentResponse): void {
    if (!comment.canDelete || this.deletingIds.has(comment.id)) return;
    const listingId = this.listing.id;
    this.deletingIds.add(comment.id);
    this.loadError = '';
    this.commentService.deleteComment(listingId, comment.id)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.deletingIds.delete(comment.id)))
      .subscribe({
        next: () => {
          if (listingId !== this.listing.id) return;
          this.comments = this.comments.filter((entry) => entry.id !== comment.id);
        },
        error: () => this.loadError = 'Nie udało się usunąć wiadomości. Odśwież dyskusję i spróbuj ponownie.'
      });
  }

  private loadInitial(): void {
    const listingId = this.listing.id;
    this.comments = [];
    this.nextOlderPage = 2;
    this.hasMore = false;
    this.loadError = '';
    this.draft = '';
    this.loading = true;
    this.commentService.getComments(listingId)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.loading = false))
      .subscribe({
        next: (page) => {
          if (listingId !== this.listing.id) return;
          this.mergeComments(page.content);
          this.hasMore = !page.last;
          this.scrollToBottom();
        },
        error: () => this.loadError = 'Nie udało się wczytać dyskusji.'
      });
  }

  private refreshLatest(): void {
    if (this.loading || this.loadingOlder) return;
    const listingId = this.listing.id;
    const container = this.messages?.nativeElement;
    const nearBottom = !container || container.scrollHeight - container.scrollTop - container.clientHeight < 120;
    this.commentService.getComments(listingId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (listingId !== this.listing.id) return;
          this.mergeComments(page.content);
          if (nearBottom) this.scrollToBottom();
        },
        error: () => { /* The next refresh can recover without interrupting the discussion. */ }
      });
  }

  private mergeComments(incoming: ListingCommentResponse[]): void {
    const byId = new Map(this.comments.map((comment) => [comment.id, comment]));
    incoming.forEach((comment) => byId.set(comment.id, comment));
    this.comments = [...byId.values()].sort((a, b) => a.id - b.id);
  }

  private scrollToBottom(): void {
    setTimeout(() => {
      const container = this.messages?.nativeElement;
      if (container) container.scrollTop = container.scrollHeight;
    });
  }
}
