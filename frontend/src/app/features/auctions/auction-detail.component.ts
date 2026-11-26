import { ConversationReportDialogComponent } from '../messages/conversation-report-dialog.component';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { ItemPreviewComponent } from '../../shared/item-preview/item-preview.component';
import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectorRef, Component, DestroyRef, HostListener, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { auditTime, filter, forkJoin, skip } from 'rxjs';
import { ListingService } from '../../core/services/listing.service';
import { Auction, AuctionBid, AuctionService } from '../../core/services/auction.service';
import { AuthService } from '../../core/services/auth.service';
import { ChatRealtimeService } from '../../core/services/chat-realtime.service';
import { PageResponse } from '../../core/models/api.models';
import { auctionAmount, auctionRemaining, auctionStatus } from '../../core/utils/auction-format';
import { parseGoldMillions, parsePln } from '../../core/utils/gold-format';
import { DiscussionDrawerComponent } from '../market/discussion-drawer.component';
import { PrivateDetailComponent } from '../../shared/private-detail/private-detail.component';

@Component({
  selector: 'mm-auction-detail',
  standalone: true,
  imports: [
    ConversationReportDialogComponent,
    PaginationComponent,
    ItemPreviewComponent,
    RouterLink,
    FormsModule,
    DatePipe,
    DiscussionDrawerComponent,
    PrivateDetailComponent,
  ],
  templateUrl: './auction-detail.component.html',
  styleUrl: './auctions.component.css',
})
export class AuctionDetailComponent {
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly service = inject(AuctionService);
  private readonly listings = inject(ListingService);
  protected watching = false;
  protected watchBusy = false;
  private watchSession = 0;
  protected readonly auth = inject(AuthService);
  private readonly socket = inject(ChatRealtimeService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  protected id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  protected auction: Auction | null = null;
  protected history: PageResponse<AuctionBid> | null = null;
  protected input = '';
  protected reason = '';
  protected dialog: { kind: string; bid?: AuctionBid } | null = null;
  protected dialogError = '';
  protected get dialogTitle() {
    return (
      (
        {
          BID_REPORT: 'Zgłoś ofertę',
          REPORT: 'Zgłoś licytację',
          INVALIDATE: 'Unieważnij ofertę',
          CANCEL: 'Anuluj licytację',
          DISPUTE: 'Zgłoś problem z realizacją',
          FULFILLMENT_CANCEL: 'Anuluj realizację',
          RESUME: 'Wznów realizację',
        } as Record<string, string>
      )[this.dialog?.kind ?? ''] ?? ''
    );
  }
  protected get dialogDescription() {
    const b = this.dialog?.bid;
    return b
      ? 'Oferta: ' +
          b.bidder +
          ', ' +
          (b.amount === null ? '' : this.amount(b.amount, b.currency)) +
          '. Opisz powód zgłoszenia lub decyzji.'
      : 'Licytacja: ' +
          this.auction?.listing.itemName +
          '. Opisz sytuację, aby umożliwić jej rozpatrzenie.';
  }
  protected error = '';
  protected notice = '';
  protected busy = false;
  protected loading = false;
  protected admin = false;
  protected attemptsPerMinute = 30;
  protected attemptSpacingSeconds = 2;
  protected discussion = false;
  protected remaining = '';
  protected expired = false;
  protected readonly amount = auctionAmount;
  protected readonly status = auctionStatus;
  private generation = 0;
  private serverOffset = 0;
  private lastLoaded = 0;
  private pendingBid: { amount: number; key: string } | null = null;
  constructor() {
    this.auth.user$.pipe(takeUntilDestroyed()).subscribe((u) => {
      this.admin = u?.role === 'admin';
      this.watchSession++;
      this.watching = false;
      this.watchBusy = false;
      if (u) {
        this.socket.connect(this.admin);
        this.socket.watchAuction(this.id);
      } else this.socket.stopAuction();
    });
    this.auth.user$.pipe(skip(1), takeUntilDestroyed()).subscribe(() => {
      this.auction = null;
      this.history = null;
      this.pendingBid = null;
      this.dialog = null;
      this.refresh();
    });
    this.socket.events$
      .pipe(
        filter((e) => e.kind === 'connected' || (e.kind === 'auction' && e.auctionId === this.id)),
        auditTime(500),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.refresh());
    const clock = setInterval(() => this.tick(), 1000);
    const poll = setInterval(() => {
      if (document.hidden || this.busy || this.loading) return;
      if (!this.socket.connected || (this.expired && this.auction?.status === 'ACTIVE'))
        this.refresh();
    }, 10000);
    this.destroy.onDestroy(() => {
      clearInterval(clock);
      clearInterval(poll);
      this.socket.stopAuction();
      this.generation++;
    });
    this.service
      .config()
      .pipe(takeUntilDestroyed())
      .subscribe((c) => {
        this.attemptsPerMinute = c.attemptsPerMinute ?? 30;
        this.attemptSpacingSeconds = c.attemptSpacingSeconds ?? 2;
      });
    this.refresh();
  }
  @HostListener('document:visibilitychange') visible() {
    if (!document.hidden && !this.loading && Date.now() - this.lastLoaded >= 10000) this.refresh();
  }
  protected refresh(page = this.history?.page ?? 1) {
    if (!Number.isSafeInteger(this.id) || this.id < 1) {
      this.error = 'Nieprawidłowy adres licytacji.';
      return;
    }
    const generation = ++this.generation;
    this.loading = true;
    forkJoin({ auction: this.service.get(this.id), history: this.service.bids(this.id, page) })
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: ({ auction, history }) => {
          if (generation !== this.generation) return;
          this.auction = auction;
          this.history = history;
          this.lastLoaded = Date.now();
          this.serverOffset = Date.parse(auction.serverTime) - Date.now();
          this.tick();
          this.loading = false;
          if (this.auth.isLoggedIn && auction.status === 'ACTIVE') {
            this.listings
              .favoriteIds([auction.id])
              .pipe(takeUntilDestroyed(this.destroy))
              .subscribe({
                next: (ids) => {
                  if (generation === this.generation) this.watching = ids.includes(auction.id);
                },
                error: () => {
                  if (generation === this.generation)
                    this.notice = 'Nie udało się wczytać stanu obserwowania.';
                },
              });
          }
        },
        error: (e) => {
          if (generation !== this.generation) return;
          this.loading = false;
          this.error = this.message(e);
        },
      });
  }
  private tick() {
    if (!this.auction) return;
    const now = Date.now() + this.serverOffset;
    this.remaining = auctionRemaining(this.auction.endsAt, now);
    this.expired = now >= Date.parse(this.auction.endsAt);
  }
  protected toggleWatch() {
    if (!this.auth.isLoggedIn || this.watchBusy || !this.auction) return;
    const session = this.watchSession;
    const remove = this.watching;
    this.watchBusy = true;
    (remove ? this.listings.removeFavorite(this.id) : this.listings.addFavorite(this.id))
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          if (session === this.watchSession) {
            this.watching = !remove;
            this.watchBusy = false;
          }
        },
        error: () => {
          if (session === this.watchSession) {
            this.watchBusy = false;
            this.error = 'Nie udało się zmienić obserwowanych.';
          }
        },
      });
  }
  protected bid() {
    const a = this.auction;
    if (!a || this.busy) return;
    const value = a.currency === 'PLN' ? parsePln(this.input) : parseGoldMillions(this.input);
    if (value === null || a.nextMinimumBid === null || value < a.nextMinimumBid) {
      this.error =
        value === null
          ? 'Wpisz poprawną kwotę, np. 500m lub 2g dla złota albo 25,50 zł.'
          : 'Minimalna kolejna oferta: ' +
            (a.nextMinimumBid === null
              ? 'osiągnięto limit'
              : this.amount(a.nextMinimumBid, a.currency)) +
            '. Podbicie wynosi co najmniej 1%, minimum 1 zł lub 1m złota.';
      return;
    }
    if (!this.pendingBid || this.pendingBid.amount !== value)
      this.pendingBid = { amount: value, key: crypto.randomUUID() };
    this.busy = true;
    this.error = '';
    this.notice = '';
    this.service
      .bid(this.id, value, a.currency, this.pendingBid.key)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: () => {
          this.busy = false;
          this.pendingBid = null;
          this.input = '';
          this.notice = 'Oferta została przyjęta.';
          this.refresh(1);
        },
        error: (e) => {
          this.busy = false;
          if (e instanceof HttpErrorResponse && e.status > 0 && e.status < 500)
            this.pendingBid = null;
          this.error = this.message(e);
          this.refresh();
        },
      });
  }
  protected start() {
    if (this.busy) return;
    this.busy = true;
    this.service
      .start(this.id)
      .pipe(takeUntilDestroyed(this.destroy))
      .subscribe({
        next: (r) => {
          this.busy = false;
          void this.router.navigate(['/messages', r.conversationId]);
        },
        error: (e) => {
          this.busy = false;
          this.error = this.message(e);
        },
      });
  }
  protected openDialog(kind: string, bid?: AuctionBid) {
    if (this.busy) return;
    this.dialog = { kind, bid };
    this.dialogError = '';
  }
  protected submitDialog(value: { details: string }) {
    if (!this.dialog || this.busy || !value.details.trim()) return;
    const { kind, bid } = this.dialog,
      reason = value.details.trim();
    let request: import('rxjs').Observable<unknown>;
    switch (kind) {
      case 'BID_REPORT':
        if (!bid) return;
        request = this.service.reportBid(this.id, bid.id, reason);
        break;
      case 'REPORT':
        request = this.service.report(this.id, reason);
        break;
      case 'INVALIDATE':
        if (!bid) return;
        request = this.service.invalidate(this.id, bid.id, reason);
        break;
      case 'CANCEL':
        request = this.service.cancel(this.id, reason);
        break;
      default:
        request = this.service.action(
          this.id,
          kind === 'FULFILLMENT_CANCEL' ? 'CANCEL' : kind,
          reason,
        );
    }
    this.busy = true;
    this.dialogError = '';
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.busy = false;
        this.dialog = null;
        this.notice =
          kind === 'BID_REPORT' || kind === 'REPORT'
            ? 'Zgłoszenie trafiło do administratora.'
            : 'Zapisano zmianę.';
        this.changeDetector.markForCheck();
        this.refresh();
      },
      error: (e) => {
        this.busy = false;
        this.dialogError = this.message(e);
        this.changeDetector.markForCheck();
      },
    });
  }
  protected action(action: string) {
    this.mutate(this.service.action(this.id, action, this.reason));
  }
  protected report() {
    this.mutate(this.service.report(this.id, this.reason), 'Zgłoszenie zapisane.');
  }
  protected cancel() {
    this.mutate(this.service.cancel(this.id, this.reason));
  }
  protected invalidate(bidId: number) {
    this.mutate(this.service.invalidate(this.id, bidId, this.reason));
  }
  private mutate(request: import('rxjs').Observable<unknown>, notice = 'Zapisano zmianę.') {
    if (this.busy) return;
    this.busy = true;
    this.error = '';
    this.notice = '';
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => {
        this.busy = false;
        this.reason = '';
        this.notice = notice;
        this.refresh();
      },
      error: (e) => {
        this.busy = false;
        this.error = this.message(e);
      },
    });
  }
  private message(e: unknown) {
    return e instanceof HttpErrorResponse
      ? (e.error?.message ?? 'Nie udało się pobrać lub zapisać danych. Odśwież i spróbuj ponownie.')
      : 'Nie udało się wykonać operacji.';
  }
}
