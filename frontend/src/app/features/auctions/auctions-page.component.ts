import { DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { skip } from 'rxjs';
import { Auction, AuctionService } from '../../core/services/auction.service';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { LookupResponse, PageResponse } from '../../core/models/api.models';
import { auctionAmount, auctionRemaining, auctionStatus } from '../../core/utils/auction-format';
import { PrivateDetailComponent } from '../../shared/private-detail/private-detail.component';

@Component({
  selector: 'mm-auctions-page', standalone: true, imports: [RouterLink, FormsModule, DatePipe, PrivateDetailComponent],
  templateUrl: './auctions-page.component.html', styleUrl: './auctions.component.css'
})
export class AuctionsPageComponent {
  protected readonly auth = inject(AuthService);
  private readonly service = inject(AuctionService);
  private readonly destroy = inject(DestroyRef);
  protected result: PageResponse<Auction> | null = null;
  protected worlds: LookupResponse[] = [];
  protected filters = {serverId: '', currency: '', search: '', scope: 'ACTIVE'};
  protected enabled = false;
  protected loading = false;
  protected error = '';
  protected admin = false;
  protected readonly amount = auctionAmount;
  protected readonly status = auctionStatus;
  private generation = 0;
  private receivedAt = Date.now();
  protected now = Date.now();
  constructor() {
    const requestedScope = inject(ActivatedRoute).snapshot.queryParamMap.get('scope');
    if (requestedScope && ['SELLING','BIDDING','ADMIN'].includes(requestedScope) && this.auth.isLoggedIn) this.filters.scope = requestedScope;
    inject(DictionaryService).getAll().pipe(takeUntilDestroyed()).subscribe(d => this.worlds = d.servers);
    this.service.config().pipe(takeUntilDestroyed()).subscribe(c => this.enabled = c.enabled);
    this.auth.user$.pipe(takeUntilDestroyed()).subscribe(user => this.admin = user?.role === 'admin');
    this.auth.user$.pipe(skip(1),takeUntilDestroyed()).subscribe(() => { this.result = null; this.filters.scope = 'ACTIVE'; this.load(); });
    const timer = setInterval(() => this.visible(), 30000);
    const clock = setInterval(() => this.now = Date.now(), 1000);
    this.destroy.onDestroy(() => { clearInterval(timer); clearInterval(clock); this.generation++; });
    this.load();
  }
  @HostListener('document:visibilitychange') visible() {
    if (!document.hidden && !this.loading && Date.now() - this.receivedAt >= 30000) this.load(this.result?.page ?? 1);
  }
  protected load(page = 1) {
    const generation = ++this.generation;
    this.loading = true;
    this.service.list({...this.filters, page}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: r => { if (generation !== this.generation) return; this.receivedAt = Date.now(); this.now = this.receivedAt; this.result = r; this.loading = false; this.error = ''; },
      error: () => { if (generation !== this.generation) return; this.loading = false; this.result = null; this.error = 'Nie udało się pobrać licytacji. Użyj przycisku Odśwież.'; }
    });
  }
  protected remaining(a: Auction) { return auctionRemaining(a.endsAt, Date.parse(a.serverTime) + this.now - this.receivedAt); }
}
