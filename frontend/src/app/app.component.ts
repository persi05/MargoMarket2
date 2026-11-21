import { HttpClient } from '@angular/common/http';
import { effect } from '@angular/core';
import { AccountAccessService, AccountBlock } from './core/services/account-access.service';
import { AccountBlockedComponent } from './features/account-blocked/account-blocked.component';
import { environment } from '../environments/environment';
import { AsyncPipe } from '@angular/common';
import { Component, DestroyRef, ElementRef, HostListener, ViewChild, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, fromEvent, merge, of, switchMap, timer } from 'rxjs';

import { AuthService } from './core/services/auth.service';
import { ChatRealtimeService } from './core/services/chat-realtime.service';
import { ConversationService } from './core/services/conversation.service';

@Component({
  selector: 'mm-root',
  standalone: true,
  imports: [AccountBlockedComponent, AsyncPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  @ViewChild('accountMenu') private accountMenu?: ElementRef<HTMLDetailsElement>;
  protected readonly authService = inject(AuthService);
  protected readonly user$ = this.authService.user$;
  private readonly conversations = inject(ConversationService);
  private readonly realtime = inject(ChatRealtimeService);
  private readonly destroyRef = inject(DestroyRef);
  protected readonly accountAccess=inject(AccountAccessService);
  private readonly http=inject(HttpClient);
  protected unreadMessages = 0;
  /** Main navigation folded under a button on narrow screens. */
  protected readonly menuOpen = signal(false);

  constructor() {
    inject(Router).events.pipe(filter(event => event instanceof NavigationEnd), takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.menuOpen.set(false));
    effect(()=>{if(this.accountAccess.block())this.realtime.disconnect();else{const user=this.authService.currentUser;if(user)this.realtime.connect(user.role==='admin');}});
    this.user$.pipe(switchMap(user=>{
      this.accountAccess.clear();
      return user ? merge(of(null),this.realtime.events$.pipe(filter(event=>event.kind==='account-access')),fromEvent(document,'visibilitychange').pipe(filter(()=>!document.hidden))).pipe(switchMap(()=>this.http.get<{accountBlock:AccountBlock|null}>(environment.apiUrl+'/auth/moderation').pipe(catchError(()=>of(null))))) : of(null);
    }),takeUntilDestroyed(this.destroyRef)).subscribe(status=>{if(status){if(status.accountBlock)this.accountAccess.show(status.accountBlock);else this.accountAccess.clear();}});

    this.user$.pipe(
      switchMap(user => {
        if (!user) {
          this.realtime.disconnect();
          return of({ count: 0 });
        }
        this.realtime.connect(user.role === 'admin');
        return merge(
          of(null),
          fromEvent(document, 'visibilitychange'),
          this.realtime.events$.pipe(filter(event => event.kind === 'connected'
            || event.kind === 'personal' || event.kind === 'read')),
          timer(30000, 30000).pipe(filter(() => !this.realtime.connected))
        ).pipe(
          filter(() => !document.hidden && !this.accountAccess.block()),
          switchMap(() => this.conversations.unreadCount().pipe(
            catchError(() => of({ count: this.unreadMessages }))
          ))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => this.unreadMessages = result.count);
  }

  logout(): void {
    this.closeAccountMenu();
    this.authService.logout();
  }

  protected toggleMenu(event: MouseEvent): void {
    event.stopPropagation();
    this.closeAccountMenu();
    this.menuOpen.update(open => !open);
  }

  protected closeMenuOnLink(event: MouseEvent): void {
    if (event.target instanceof Element && event.target.closest('a')) this.menuOpen.set(false);
  }

  protected closeAccountMenu(): void {
    if (this.accountMenu) this.accountMenu.nativeElement.open = false;
  }

  @HostListener('document:click', ['$event'])
  protected dismissAccountMenu(event: MouseEvent): void {
    if (event.target instanceof Node && !this.accountMenu?.nativeElement.contains(event.target)) this.closeAccountMenu();
    if (event.target instanceof Element && !event.target.closest('#main-nav, .menu-toggle')) this.menuOpen.set(false);
  }

  @HostListener('document:keydown.escape')
  protected escapeAccountMenu(): void {
    if (this.menuOpen()) {
      this.menuOpen.set(false);
      document.querySelector<HTMLElement>('.menu-toggle')?.focus();
    }
    const menu = this.accountMenu?.nativeElement;
    if (!menu?.open) return;
    menu.open = false;
    menu.querySelector('summary')?.focus();
  }

  initial(username: string): string {
    return username.charAt(0).toLocaleUpperCase('pl-PL');
  }
}
