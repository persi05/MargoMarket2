import { MarketLayoutComponent } from '../../shared/market-layout/market-layout.component';
import { MarketFilterComponent } from '../../shared/market-layout/market-filter.component';
import { AuctionItemComponent } from '../../shared/auction-item/auction-item.component';
import { DatePipe } from '@angular/common';
import { Component, DestroyRef, HostListener, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterLink } from '@angular/router';
import { skip } from 'rxjs';
import { Auction, AuctionService } from '../../core/services/auction.service';
import { AuthService } from '../../core/services/auth.service';
import { DictionaryService } from '../../core/services/dictionary.service';
import { LookupResponse, PageResponse } from '../../core/models/api.models';
import { auctionAmount, auctionRemaining, auctionStatus } from '../../core/utils/auction-format';
import { PrivateDetailComponent } from '../../shared/private-detail/private-detail.component';

@Component({
  selector: 'mm-auctions-page', standalone: true, imports: [MarketLayoutComponent, MarketFilterComponent, AuctionItemComponent, RouterLink, FormsModule, DatePipe, PrivateDetailComponent],
  templateUrl: './auctions-page.component.html', styleUrl: './auctions.component.css'
})
export class AuctionsPageComponent {
  protected readonly auth = inject(AuthService);
  private readonly service = inject(AuctionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);
  private readonly destroy = inject(DestroyRef);
  protected result: PageResponse<Auction> | null = null;
  protected worlds: LookupResponse[] = [];
  protected filters = {serverId: '', currency: '', search: '', scope: 'ACTIVE', itemTypeId: '', bound: '', minLevel: '', maxLevel: ''};
  protected types: LookupResponse[] = [];
  private appliedFilters = {...this.filters};
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

    inject(DictionaryService).getAll().pipe(takeUntilDestroyed()).subscribe(d => { this.worlds = d.servers; this.types = d.itemTypes; });
    this.service.config().pipe(takeUntilDestroyed()).subscribe(c => this.enabled = c.enabled);
    this.auth.user$.pipe(takeUntilDestroyed()).subscribe(user => this.admin = user?.role === 'admin');
    this.auth.user$.pipe(skip(1),takeUntilDestroyed()).subscribe(() => { this.result = null; this.filters.scope = 'ACTIVE'; this.load(); });
    const timer = setInterval(() => this.visible(), 30000);
    const clock = setInterval(() => this.now = Date.now(), 1000);
    this.destroy.onDestroy(() => { clearInterval(timer); clearInterval(clock); this.generation++; });
    this.route.queryParamMap.pipe(takeUntilDestroyed()).subscribe(params => {
      const scope = params.get('scope') ?? 'ACTIVE';
      this.filters.scope = ['ACTIVE','ENDED'].includes(scope) || (this.auth.isLoggedIn && ['SELLING','BIDDING','ADMIN'].includes(scope)) ? scope : 'ACTIVE';
      this.load();
    });
  }
  @HostListener('document:visibilitychange') visible() {
    if (!document.hidden && !this.loading && Date.now() - this.receivedAt >= 30000) this.load(this.result?.page ?? 1,false);
  }
  protected load(page = 1, apply = true) {
    const generation = ++this.generation;
    this.loading = true;
    if (apply) this.appliedFilters = {...this.filters};
    this.service.list({...this.appliedFilters, page}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next: r => { if (generation !== this.generation) return; this.receivedAt = Date.now(); this.now = this.receivedAt; this.result = r; this.loading = false; this.error = ''; },
      error: () => { if (generation !== this.generation) return; this.loading = false; this.result = null; this.error = 'Nie udało się pobrać licytacji. Spróbuj ponownie, klikając Filtruj.'; }
    });
  }
  protected setScope(scope: string) {
    void this.router.navigate([], {relativeTo:this.route,queryParams:{scope:scope === 'ACTIVE' ? null : scope},queryParamsHandling:'merge'});
  }
  protected resetFilters() { const scope = this.filters.scope; this.filters = {serverId:'',currency:'',search:'',scope,itemTypeId:'',bound:'',minLevel:'',maxLevel:''}; this.load(); }
  protected remaining(a: Auction) { return auctionRemaining(a.endsAt, Date.parse(a.serverTime) + this.now - this.receivedAt); }
}
