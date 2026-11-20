import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';
@Component({selector:'mm-market-layout',standalone:true,imports:[RouterLink],template: `
  <section class="market-layout-shell">
    <aside class="market-left-rail"><ng-content select="[marketFilter]" /></aside>
    <div class="market-center">
      <nav class="market-navigation" aria-label="Rodzaje ofert"><a routerLink="/" [attr.aria-current]="active === 'items' ? 'page' : null">Przedmioty</a><a routerLink="/gold" [attr.aria-current]="active === 'gold' ? 'page' : null">Złoto</a><a routerLink="/auctions" [attr.aria-current]="active === 'auctions' ? 'page' : null">Licytacje</a></nav>
      <ng-content />
    </div>
    <div class="market-right-rail" aria-hidden="true"></div>
  </section>
`, styles:[`
:host { display:block; --market-rail:368px; }
.market-layout-shell { display:grid; grid-template-columns:var(--market-rail) minmax(0,1fr) var(--market-rail); min-height:calc(100vh - 71px); }
.market-left-rail, .market-right-rail { position:relative; min-width:0; }
.market-left-rail::before, .market-right-rail::before { content:''; position:fixed; top:71px; bottom:0; width:var(--market-rail); background:rgba(0,0,0,.2); pointer-events:none; }
.market-left-rail::before { left:0; border-right:1px solid var(--color-border); }
.market-right-rail::before { right:0; border-left:1px solid var(--color-border); }
.market-center { min-width:0; width:100%; max-width:1280px; justify-self:center; padding:0 clamp(16px,2vw,32px) 32px; }
.market-navigation { margin:24px 0 28px; }
@media(max-width:1100px) { .market-layout-shell { grid-template-columns:1fr; } .market-left-rail { padding:20px; } .market-left-rail::before, .market-right-rail { display:none; } .market-center { padding-inline:20px; } }
@media(max-width:620px) { .market-left-rail { padding:12px; } .market-center { padding-inline:12px; } .market-navigation a { padding:12px 8px; font-size:13px; } }
`]})
export class MarketLayoutComponent { @Input() active: 'items'|'gold'|'auctions' = 'items'; }
