import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({selector:'mm-market-layout',standalone:true,imports:[RouterLink],template: `
  <section class="market-layout-shell">
    <div class="market-nav-wrap"><nav class="market-navigation" aria-label="Rodzaje ofert"><a routerLink="/items" [attr.aria-current]="active === 'items' ? 'page' : null">Przedmioty</a><a routerLink="/gold" [attr.aria-current]="active === 'gold' ? 'page' : null">Złoto</a><a routerLink="/auctions" [attr.aria-current]="active === 'auctions' ? 'page' : null">Licytacje</a></nav></div>
    <aside class="market-left-rail"><ng-content select="[marketFilter]" /></aside>
    <div class="market-center">
      <ng-content />
    </div>
    <div class="market-right-rail" aria-hidden="true"></div>
  </section>
`, styles:[`
/* The centre keeps 1030px so the market table fits without squeezing. Above that both rails shrink together
   until the left one reaches the 300px the filter needs (~1630px); then only the right rail shrinks, down to
   nothing at ~1330px. Below that the centre narrows, and below 1100px the filter moves above the list. */
:host { display:block; --market-rail:clamp(300px, (100vw - 1030px) / 2, 368px); --market-rail-right:clamp(0px, min((100vw - 1030px) / 2, 100vw - 1330px), 368px); }
.market-layout-shell { display:grid; grid-template-columns:var(--market-rail) minmax(0,1fr) var(--market-rail-right); grid-template-rows:auto 1fr; min-height:calc(100vh - 71px); }
/* The tabs come first in the markup so that on phones they sit above the filter; on wide screens they go back over the list. */
.market-nav-wrap { grid-column:2; grid-row:1; min-width:0; width:100%; max-width:1280px; justify-self:center; padding:0 clamp(16px,2vw,32px); }
.market-left-rail { grid-column:1; grid-row:1 / span 2; }
.market-center { grid-column:2; grid-row:2; }
.market-right-rail { grid-column:3; grid-row:1 / span 2; }
.market-left-rail, .market-right-rail { position:relative; min-width:0; }
.market-left-rail::before, .market-right-rail::before { content:''; position:fixed; top:71px; bottom:0; width:var(--market-rail); background:rgba(0,0,0,.2); pointer-events:none; }
.market-left-rail::before { left:0; border-right:1px solid var(--color-border); }
.market-right-rail::before { right:0; width:var(--market-rail-right); border-left:1px solid var(--color-border); }
@media(max-width:1330px) { .market-right-rail::before { display:none; } }
.market-center { min-width:0; width:100%; max-width:1280px; justify-self:center; padding:0 clamp(16px,2vw,32px) 32px; }
.market-navigation { margin:24px 0 28px; }
@media(max-width:1100px) { .market-layout-shell { grid-template-columns:1fr; grid-template-rows:none; align-content:start; } .market-nav-wrap, .market-left-rail, .market-center { grid-column:1; grid-row:auto; } .market-nav-wrap { padding-inline:20px; } .market-navigation { margin:16px 0 0; } .market-left-rail { padding:16px 20px; } .market-left-rail::before, .market-right-rail { display:none; } .market-center { padding-inline:20px; } }
@media(max-width:620px) { .market-nav-wrap { padding-inline:12px; } .market-left-rail { padding:12px; } .market-center { padding-inline:12px; } .market-navigation a { padding:12px 8px; font-size:13px; } }
`]})
export class MarketLayoutComponent { @Input() active: 'items'|'gold'|'auctions' = 'items'; }
