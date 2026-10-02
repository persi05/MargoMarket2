import { DatePipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, HostListener, Input, OnChanges, OnDestroy, OnInit, Output, EventEmitter, SimpleChanges, ViewChild, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { finalize } from 'rxjs';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';

import { CommentPostingStatus, ListingCommentResponse, ListingResponse } from '../../core/models/api.models';
import { AuthService } from '../../core/services/auth.service';
import { ListingCommentService } from '../../core/services/listing-comment.service';
import { formatListingPrice } from '../../core/utils/price-format';
import { PrivateDetailComponent } from '../../shared/private-detail/private-detail.component';
import { DiscussionPostingClock, formatMuteDeadline } from './discussion-posting-clock';

@Component({
  selector: 'mm-discussion-drawer',
  standalone: true,
  imports: [DatePipe, FormsModule, RouterLink, PrivateDetailComponent],
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
  private countdownTimer?: ReturnType<typeof setInterval>;
  private readonly postingClock = new DiscussionPostingClock();
  private postingVersion = 0;

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
  protected isAdmin = false;
  protected sendNotice = '';
  protected retryAfterSeconds = 0;
  protected muteRemainingSeconds = 0;
  protected loadingPostingStatus = false;
  private nextOlderPage = 2;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['listing'] && this.listing) {
      this.loadInitial();
    }
  }

  ngOnInit(): void {
    this.authService.user$.pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((user) => {
        this.currentUserId = user?.id ?? null;
        this.isAdmin = user?.role === 'admin';
        this.postingClock.reset();
        this.updateCountdown();
        this.sendNotice = '';
        this.loadPostingStatus();
      });
    this.refreshTimer = setInterval(() => this.refreshLatest(), 12000);
    this.countdownTimer = setInterval(() => this.updateCountdown(), 1000);
    setTimeout(() => this.closeButton?.nativeElement.focus());
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }
    if (this.countdownTimer) clearInterval(this.countdownTimer);
  }

  @HostListener('document:keydown.escape')
  protected closeOnEscape(): void {
    this.closed.emit();
  }

  @HostListener('window:focus')
  @HostListener('window:pageshow')
  @HostListener('document:visibilitychange')
  protected syncPostingStatus(): void {
    if (document.visibilityState === 'visible') this.loadPostingStatus();
  }

  protected get priceLabel(): string {
    return formatListingPrice(this.listing.price, this.listing.currency.name);
  }

  protected get canSend(): boolean {
    const body = this.draft.trim();
    return !!body && body.length <= 1000 && !this.sending
      && (this.isAdmin || (!this.loadingPostingStatus && this.retryAfterSeconds === 0))
      && this.listing.status === 'active';
  }

  protected get cooldownLabel(): string {
    return `${Math.floor(this.retryAfterSeconds / 60)}:${String(this.retryAfterSeconds % 60).padStart(2, '0')}`;
  }

  protected get muteLabel(): string {
    const hours = Math.floor(this.muteRemainingSeconds / 3600);
    const minutes = Math.floor(this.muteRemainingSeconds % 3600 / 60);
    const seconds = this.muteRemainingSeconds % 60;
    return `${hours}:${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  protected get muteEndLabel(): string {
    return this.postingClock.mutedUntil ? formatMuteDeadline(this.postingClock.mutedUntil) : '';
  }

  private loadPostingStatus(): void {
    const version = ++this.postingVersion;
    const listingId = this.listing.id;
    const authorId = this.currentUserId;
    if (authorId === null || this.listing.status !== 'active') {
      this.loadingPostingStatus = false;
      return;
    }
    this.loadingPostingStatus = true;
    this.commentService.postingStatus(listingId).pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: status => {
        if (version !== this.postingVersion || listingId !== this.listing.id || authorId !== this.currentUserId) return;
        this.loadingPostingStatus = false;
        this.setPostingStatus(status);
      },
      error: () => {
        if (version !== this.postingVersion || listingId !== this.listing.id || authorId !== this.currentUserId) return;
        this.loadingPostingStatus = false;
        this.sendError = 'Nie udało się pobrać limitu wysyłania. Serwer sprawdzi go przy wysłaniu.';
      }
    });
  }

  private setPostingStatus(status: CommentPostingStatus): void {
    this.postingClock.set(status);
    this.updateCountdown();
  }

  private updateCountdown(): void {
    this.retryAfterSeconds = this.postingClock.retryAfterSeconds;
    this.muteRemainingSeconds = this.postingClock.muteRemainingSeconds;
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
    if (!this.canSend) {
      return;
    }
    const listingId = this.listing.id;
    const authorId = this.currentUserId;
    this.sending = true;
    this.sendError = '';
    this.sendNotice = '';
    this.commentService.addComment(listingId, body)
      .pipe(takeUntilDestroyed(this.destroyRef), finalize(() => this.sending = false))
      .subscribe({
        next: (result) => {
          if (listingId !== this.listing.id || authorId !== this.currentUserId) return;
          ++this.postingVersion;
          this.loadingPostingStatus = false;
          this.draft = '';
          this.setPostingStatus(result.postingStatus);
          if (result.censored && result.postingStatus.muteRemainingSeconds > 0) {
            this.sendNotice = 'Wysłano wiadomość zawierającą obraźliwe słowo. Nałożono blokadę pisania na 12 godzin.';
          }
          this.mergeComments([result.comment]);
          this.scrollToBottom();
        },
        error: response => {
          if (listingId !== this.listing.id || authorId !== this.currentUserId) return;
          this.sendError = response.error?.message || 'Nie udało się dodać komentarza. Spróbuj ponownie.';
          if (response.status === 429) {
            ++this.postingVersion;
            this.loadingPostingStatus = false;
            if (response.error?.postingStatus) this.setPostingStatus(response.error.postingStatus);
            else this.loadPostingStatus();
          }
        }
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
    this.sendNotice = '';
    this.sendError = '';
    this.postingClock.reset();
    this.updateCountdown();
    this.loadPostingStatus();
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
    this.loadPostingStatus();
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
        error: () => { }
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
