import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AuthService } from '../../core/services/auth.service';
import { ChangeDetectorRef, Component, ElementRef, HostListener, Input, ViewChild, inject } from '@angular/core';
import { RouterLink } from '@angular/router';
import { ListingResponse } from '../../core/models/api.models';
import { ItemDescriptionPipe } from '../../core/pipes/item-description.pipe';
import { itemLastAvailableDuring, itemRequiredProfessions, itemStatLines } from '../../core/utils/item-stats';
import { hasEnhancementLevel } from '../../core/utils/item-types';
import { formatListingPrice } from '../../core/utils/price-format';
import { auctionAmount } from '../../core/utils/auction-format';
import { PrivateDetailComponent } from '../private-detail/private-detail.component';

@Component({ selector: 'mm-item-preview', standalone: true, imports: [RouterLink, ItemDescriptionPipe, PrivateDetailComponent],
  templateUrl: './item-preview.component.html', styleUrl: './item-preview.component.css' })
export class ItemPreviewComponent {
  @Input({required:true}) listing!: ListingResponse;
  @Input() auctionPrice: string | null | undefined;
  @ViewChild('panel') private panel?: ElementRef<HTMLElement>;
  private readonly changeDetector = inject(ChangeDetectorRef);
  private readonly auth = inject(AuthService);
  constructor() { this.auth.user$.pipe(takeUntilDestroyed()).subscribe(() => { this.unpin(); this.changeDetector.markForCheck(); }); }
  private readonly host = inject(ElementRef<HTMLElement>);
  private static frontIndex = 110;
  protected open = false; protected pinned = false; protected dragging = false;
  protected imageFailed = false; protected x = 12; protected y = 12; protected z = 110;
  private drag: {id:number; dx:number; dy:number} | null = null;
  protected readonly stats = itemStatLines; protected readonly occasion = itemLastAvailableDuring; protected readonly professions = itemRequiredProfessions;
  protected get showImage() { return !!this.listing.iconUrl && !this.imageFailed; }
  protected get enhancement() { return hasEnhancementLevel(this.listing.itemType?.name ?? '',this.listing.itemName) ? '+' + this.listing.enhancementLevel : '-'; }
  protected get rarityClass() {
    const name = this.listing.rarity?.name.toLowerCase() ?? '';
    return 'rarity-' + (name.includes('legendarn') ? 'legendary' : name.includes('heroiczn') ? 'heroic' : name.includes('unikat') ? 'unique' : name.includes('ulepszon') ? 'upgraded' : 'normal');
  }
  protected get price(): string | null {
    if (!this.auth.isLoggedIn) return null;
    if (this.auctionPrice !== undefined) return this.auctionPrice;
    const a=this.listing.auction;
    if (a) { const amount=a.highestAmount ?? a.startingAmount; return amount === null ? null : auctionAmount(amount,a.currency); }
    return this.listing.price === null ? null : formatListingPrice(this.listing.price,this.listing.currency?.name ?? '');
  }
  protected show(event: Event) {
    if (this.pinned || this.open) return;
    const bounds=(event.currentTarget as HTMLElement).getBoundingClientRect();
    this.x=Math.max(12,Math.min(bounds.right-2,window.innerWidth-364));
    this.y=Math.max(12,Math.min(bounds.top,window.innerHeight-240));
    this.open=true; this.front();
    setTimeout(()=>this.fit());
  }
  protected hide() { if(!this.pinned) this.open=false; }
  protected blur() { setTimeout(()=>{ if(!this.host.nativeElement.contains(document.activeElement)) this.hide(); }); }
  protected pin() { this.pinned=true; this.open=true; this.front(); }
  protected unpin() { this.pinned=false; this.open=false; this.drag=null; this.dragging=false; }
  protected front() { this.z=++ItemPreviewComponent.frontIndex; }
  protected startDrag(event: PointerEvent) {
    if(!this.pinned || !event.isPrimary || event.button!==0 || (event.target as Element).closest('a,.preview-close,.preview-pin')) return;
    this.front(); this.drag={id:event.pointerId,dx:event.clientX-this.x,dy:event.clientY-this.y}; this.dragging=true;
    (event.currentTarget as HTMLElement).setPointerCapture(event.pointerId); event.preventDefault();
  }
  @HostListener('window:pointermove',['$event']) protected move(event: PointerEvent) {
    if(event.pointerId!==this.drag?.id) return;
    this.x=event.clientX-this.drag!.dx; this.y=event.clientY-this.drag!.dy; this.fit();
  }
  @HostListener('window:pointerup',['$event']) @HostListener('window:pointercancel',['$event']) protected stop(event: PointerEvent) {
    if(event.pointerId===this.drag?.id) { this.drag=null; this.dragging=false; }
  }
  protected moveWithKeyboard(event: KeyboardEvent) {
    const shifts:Record<string,number[]>={ArrowLeft:[-24,0],ArrowRight:[24,0],ArrowUp:[0,-24],ArrowDown:[0,24]};
    const shift=shifts[event.key]; if(!shift)return;
    this.x+=shift[0];this.y+=shift[1];this.fit();event.preventDefault();
  }
  @HostListener('window:resize') protected fit() {
    const bounds=this.panel?.nativeElement.getBoundingClientRect(); if(!bounds) return;
    this.x=Math.max(12,Math.min(this.x,window.innerWidth-bounds.width-12));
    this.y=Math.max(12,Math.min(this.y,window.innerHeight-bounds.height-12));
  }
}
