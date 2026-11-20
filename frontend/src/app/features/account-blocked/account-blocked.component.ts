import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { AccountAccessService, AccountBlock } from '../../core/services/account-access.service';
import { AuthService } from '../../core/services/auth.service';

@Component({selector:'mm-account-blocked',standalone:true,imports:[DatePipe],template: `
  <main class="blocked"><section role="alert"><span class="lock" aria-hidden="true">⛔</span><h1>Twoje konto jest zablokowane</h1>
    @if(access.block();as block){<p class="reason">{{block.reason}}</p><p>{{block.expiresAt ? 'Blokada obowiązuje do:' : 'Blokada została nałożona bezterminowo.'}} @if(block.expiresAt){<strong>{{block.expiresAt | date:'dd.MM.yyyy HH:mm'}}</strong>}</p>}
    <p>Dostęp do konta będzie możliwy po wygaśnięciu lub cofnięciu blokady.</p>
    @if(error()){<p class="error">{{error()}}</p>}
    <div class="actions">@if(auth.isLoggedIn){<button type="button" class="mm-button" [disabled]="busy()" (click)="check()">{{busy() ? 'Sprawdzanie…' : 'Sprawdź dostęp'}}</button>}<button type="button" class="mm-button secondary" (click)="logout()">{{auth.isLoggedIn ? 'Wyloguj się' : 'Wróć do logowania'}}</button></div>
  </section></main>`,styles:[`
    .blocked { min-height:100dvh; display:grid; place-items:center; padding:24px; box-sizing:border-box; }section { width:min(100%,620px); box-sizing:border-box; padding:36px; background:var(--color-card-dark); border:1px solid #8e4b44; border-radius:12px; }h1 { font-size:clamp(26px,4vw,36px); margin:16px 0; }.lock { font-size:32px; }p { color:var(--color-text-muted); line-height:1.7; }.reason { color:var(--color-text-main); white-space:pre-wrap; overflow-wrap:anywhere; }.actions { display:flex; flex-wrap:wrap; gap:12px; margin-top:24px; }.error { color:#ffd1c8; }
  `]})
export class AccountBlockedComponent {
  protected readonly access=inject(AccountAccessService);protected readonly auth=inject(AuthService);
  private readonly http=inject(HttpClient);private readonly router=inject(Router);private readonly destroy=inject(DestroyRef);
  protected readonly busy=signal(false);protected readonly error=signal('');
  constructor(){const timer=setInterval(()=>{if(this.auth.isLoggedIn && this.access.block()?.expiresAt && !document.hidden)this.check();},30000);this.destroy.onDestroy(()=>clearInterval(timer));}
  protected check(){if(this.busy())return;this.busy.set(true);this.error.set('');this.http.get<{accountBlock:AccountBlock|null}>(environment.apiUrl+'/auth/moderation').pipe(takeUntilDestroyed(this.destroy)).subscribe({next:status=>{this.busy.set(false);if(status.accountBlock)this.access.show(status.accountBlock);else{this.access.clear();void this.router.navigate(['/items']);}},error:()=>{this.busy.set(false);this.error.set('Nie udało się sprawdzić dostępu. Spróbuj ponownie.');}});}
  protected logout(){this.auth.logout();this.access.clear();void this.router.navigate(['/auth']);}
}
