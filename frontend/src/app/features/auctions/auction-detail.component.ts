import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, HostListener, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { auditTime, filter, forkJoin, skip } from 'rxjs';
import { Auction, AuctionBid, AuctionService } from '../../core/services/auction.service';
import { AuthService } from '../../core/services/auth.service';
import { ChatRealtimeService } from '../../core/services/chat-realtime.service';
import { PageResponse } from '../../core/models/api.models';
import { auctionAmount, auctionRemaining, auctionStatus } from '../../core/utils/auction-format';
import { parseGoldMillions, parsePln } from '../../core/utils/gold-format';
import { DiscussionDrawerComponent } from '../market/discussion-drawer.component';
import { PrivateDetailComponent } from '../../shared/private-detail/private-detail.component';

@Component({
  selector: 'mm-auction-detail', standalone: true,
  imports: [RouterLink, FormsModule, DatePipe, DiscussionDrawerComponent, PrivateDetailComponent],
  templateUrl: './auction-detail.component.html', styleUrl: './auctions.component.css'
})
export class AuctionDetailComponent {
  private readonly service = inject(AuctionService);
  protected readonly auth = inject(AuthService);
  private readonly socket = inject(ChatRealtimeService);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  protected id = Number(inject(ActivatedRoute).snapshot.paramMap.get('id'));
  protected auction: Auction | null = null;
  protected history: PageResponse<AuctionBid> | null = null;
  protected input = '';
  protected reason = '';
  protected error = '';
  protected notice = '';
  protected busy = false;
  protected loading = false;
  protected admin = false;
  protected enabled = false;
  protected plnEnabled = false;
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
  private pendingBid: {amount:number; key:string} | null = null;
  constructor() {
    this.auth.user$.pipe(takeUntilDestroyed()).subscribe(u => {
      this.admin = u?.role === 'admin';
      if(u) { this.socket.connect(this.admin); this.socket.watchAuction(this.id); }
      else this.socket.stopAuction();
    });
    this.auth.user$.pipe(skip(1),takeUntilDestroyed()).subscribe(() => { this.auction = null; this.history = null; this.pendingBid = null; this.refresh(); });
    this.socket.events$.pipe(
      filter(e => e.kind === 'connected' || (e.kind === 'auction' && e.auctionId === this.id)),
      auditTime(500),
      takeUntilDestroyed()
    ).subscribe(() => this.refresh());
    const clock = setInterval(() => this.tick(), 1000);
    const poll = setInterval(() => {
      if (document.hidden || this.busy || this.loading) return;
      if (!this.socket.connected || (this.expired && this.auction?.status === 'ACTIVE')) this.refresh();
    }, 10000);
    this.destroy.onDestroy(() => { clearInterval(clock); clearInterval(poll); this.socket.stopAuction(); this.generation++; });
    this.service.config().pipe(takeUntilDestroyed()).subscribe(c => {
      this.enabled = c.enabled; this.plnEnabled = c.plnEnabled;
      this.attemptsPerMinute = c.attemptsPerMinute ?? 30; this.attemptSpacingSeconds = c.attemptSpacingSeconds ?? 2;
    });
    this.refresh();
  }
  @HostListener('document:visibilitychange') visible() {
    if (!document.hidden && !this.loading && Date.now() - this.lastLoaded >= 10000) this.refresh();
  }
  protected refresh(page = this.history?.page ?? 1) {
    if (!Number.isSafeInteger(this.id) || this.id < 1) { this.error = 'Nieprawidłowy adres licytacji.'; return; }
    const generation = ++this.generation;
    this.loading = true;
    forkJoin({auction:this.service.get(this.id), history:this.service.bids(this.id,page)})
      .pipe(takeUntilDestroyed(this.destroy)).subscribe({
        next: ({auction,history}) => {
          if(generation !== this.generation) return;
          this.auction = auction; this.history = history;
          this.lastLoaded = Date.now();
          this.serverOffset = Date.parse(auction.serverTime)-Date.now();
          this.tick(); this.loading = false;
        },
        error: e => { if(generation !== this.generation) return; this.loading = false; this.error = this.message(e); }
      });
  }
  private tick() {
    if (!this.auction) return;
    const now = Date.now() + this.serverOffset;
    this.remaining = auctionRemaining(this.auction.endsAt, now);
    this.expired = now >= Date.parse(this.auction.endsAt);
  }
  protected bid() {
    const a = this.auction;
    if (!a || this.busy) return;
    const value = a.currency === 'PLN' ? parsePln(this.input) : parseGoldMillions(this.input);
    if (value === null || a.nextMinimumBid === null || value < a.nextMinimumBid) { this.error = 'Wpisz co najmniej minimalną kolejną ofertę.'; return; }
    if (!this.pendingBid || this.pendingBid.amount !== value) this.pendingBid = {amount:value,key:crypto.randomUUID()};
    this.busy = true; this.error = ''; this.notice = '';
    this.service.bid(this.id,value,a.currency,this.pendingBid.key).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.busy = false; this.pendingBid = null; this.input = ''; this.notice = 'Oferta została przyjęta.'; this.refresh(1); },
      error: e => { this.busy = false; if(e instanceof HttpErrorResponse && e.status > 0 && e.status < 500) this.pendingBid = null; this.error = this.message(e); this.refresh(); }
    });
  }
  protected start() {
    if(this.busy) return;
    this.busy = true;
    this.service.start(this.id).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: r => { this.busy = false; void this.router.navigate(['/messages',r.conversationId]); },
      error: e => { this.busy = false; this.error = this.message(e); }
    });
  }
  protected action(action: string) { this.mutate(this.service.action(this.id,action,this.reason)); }
  protected report() { this.mutate(this.service.report(this.id,this.reason),'Zgłoszenie zapisane.'); }
  protected cancel() { this.mutate(this.service.cancel(this.id,this.reason)); }
  protected invalidate(bidId: number) { this.mutate(this.service.invalidate(this.id,bidId,this.reason)); }
  private mutate(request: import('rxjs').Observable<unknown>, notice = 'Zapisano zmianę.') {
    if(this.busy) return;
    this.busy = true; this.error = ''; this.notice = '';
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: () => { this.busy = false; this.reason = ''; this.notice = notice; this.refresh(); },
      error: e => { this.busy = false; this.error = this.message(e); }
    });
  }
  private message(e: unknown) { return e instanceof HttpErrorResponse ? e.error?.message ?? 'Nie udało się pobrać lub zapisać danych. Odśwież i spróbuj ponownie.' : 'Nie udało się wykonać operacji.'; }
}
