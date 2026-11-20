import { Component, Input } from '@angular/core';
import { RouterLink } from '@angular/router';

@Component({
  selector: 'mm-sale-mode', standalone: true, imports: [RouterLink],
  template: `<nav aria-label="Rodzaj ogłoszenia">
    <a routerLink="/new" [attr.aria-current]="active === 'item' ? 'page' : null">Zwykła sprzedaż</a>
    @if (auctionsEnabled) {<a routerLink="/new" [queryParams]="{mode: 'auction'}" [attr.aria-current]="active === 'auction' ? 'page' : null">Licytacja</a>}
    @else {<span aria-disabled="true">Licytacja</span>}
    <a routerLink="/gold/new" [attr.aria-current]="active === 'gold' ? 'page' : null">Złoto</a>
  </nav>`,
  styles: [`
    :host { display:block; margin:20px 0 4px; }
    nav { display:inline-flex; padding:4px; gap:4px; background:var(--color-input-dark); border:1px solid var(--color-border); border-radius:10px; max-width:100%; }
    a, span { padding:11px 20px; border-radius:7px; color:var(--color-text-muted); text-decoration:none; font-weight:700; text-align:center; }
    a[aria-current="page"] { background:var(--color-primary); color:#182215; }
    a:hover { color:var(--color-text-main); }
    a[aria-current="page"]:hover { color:#182215; }
    a:focus-visible { outline:2px solid var(--color-primary); outline-offset:2px; }
    span { opacity:.45; }
    @media(max-width:500px) { nav { display:flex; } a, span { flex:1; padding:11px 8px; font-size:13px; } }
  `]
})
export class SaleModeComponent {
  @Input() active: 'item' | 'auction' | 'gold' = 'item';
  @Input() auctionsEnabled = true;
}
