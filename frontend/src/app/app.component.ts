import { AsyncPipe } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, filter, fromEvent, merge, of, switchMap, timer } from 'rxjs';

import { AuthService } from './core/services/auth.service';
import { ChatRealtimeService } from './core/services/chat-realtime.service';
import { ConversationService } from './core/services/conversation.service';

@Component({
  selector: 'mm-root',
  standalone: true,
  imports: [AsyncPipe, RouterLink, RouterLinkActive, RouterOutlet],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  protected readonly authService = inject(AuthService);
  protected readonly user$ = this.authService.user$;
  private readonly conversations = inject(ConversationService);
  private readonly realtime = inject(ChatRealtimeService);
  private readonly destroyRef = inject(DestroyRef);
  protected unreadMessages = 0;

  constructor() {
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
          filter(() => !document.hidden),
          switchMap(() => this.conversations.unreadCount().pipe(
            catchError(() => of({ count: this.unreadMessages }))
          ))
        );
      }),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => this.unreadMessages = result.count);
  }

  logout(): void {
    this.authService.logout();
  }

  initial(username: string): string {
    return username.charAt(0).toLocaleUpperCase('pl-PL');
  }
}
