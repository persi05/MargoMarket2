import { Component, Input } from '@angular/core';
import { ListingResponse } from '../../core/models/api.models';
import { ItemDescriptionPipe } from '../../core/pipes/item-description.pipe';
import { itemStatLines, itemRequiredProfessions } from '../../core/utils/item-stats';
@Component({
  selector: 'mm-auction-item', standalone: true, imports: [ItemDescriptionPipe],
  template: `
    <span class="frame" tabindex="0" [attr.aria-label]="'Statystyki: ' + listing.itemName" (mouseenter)="position($event)" (focus)="position($event)">
      @if (listing.iconUrl && !broken) { <img [src]="listing.iconUrl" [alt]="listing.itemName" (error)="broken = true" loading="lazy"> }
      @else { <span>{{listing.itemName.charAt(0)}}</span> }
      <span class="preview" role="tooltip" [style.left.px]="x" [style.top.px]="y">
        <strong>{{listing.itemName}}</strong><b>{{listing.rarity?.name}}</b>
        <span>{{listing.itemType?.name}} · Poziom {{listing.level}}</span>
        <span>Ulepszenie: +{{listing.enhancementLevel}} · Związane: {{listing.bound ? 'Tak' : 'Nie'}}</span>
        @for (stat of stats(listing.itemStats); track stat.label) {<span>{{stat.label}}: <b>{{stat.value}}</b></span>}
        @if (listing.itemDescription) {<span>{{listing.itemDescription | itemDescription:listing.itemStats}}</span>}
        @if (professions(listing.itemStats); as p) {<span>Wymagane profesje: {{p}}</span>}
      </span>
    </span>
  `,
  styles: [`
    :host { display: inline-block; }
    .frame { display: grid; place-items: center; width: 48px; height: 48px; border: 1px solid #80654c; border-radius: 5px; background: #201914; cursor: help; }
    img { max-width: 40px; max-height: 40px; image-rendering: pixelated; }
    .preview { display: none; position: fixed; z-index: 120; width: min(330px,calc(100vw - 24px)); max-height: 65vh; overflow: auto; padding: 18px; background: #201914; border: 1px solid #a8c98a; border-radius: 8px; box-shadow: 0 12px 40px #0009; color: #e7ddc9; font-size: 13px; line-height: 1.5; white-space: normal; pointer-events: none; }
    .preview strong { font-size: 16px; color: #a8c98a; }
    .preview > * { display: block; }
    .frame:hover .preview, .frame:focus .preview { display: grid; gap: 5px; }
    .frame:focus-visible { outline: 2px solid #a8c98a; outline-offset: 3px; }
  `]
})
export class AuctionItemComponent {
  @Input({required:true}) listing!: ListingResponse;
  protected broken = false;
  protected x = 12;
  protected y = 12;
  protected readonly stats = itemStatLines;
  protected readonly professions = itemRequiredProfessions;
  protected position(event: Event) {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    this.x = Math.max(12, Math.min(rect.right + 12, window.innerWidth - 354));
    this.y = Math.max(12, Math.min(rect.top, window.innerHeight * .3));
  }
}
