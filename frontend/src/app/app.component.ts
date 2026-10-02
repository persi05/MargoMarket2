import { AsyncPipe } from '@angular/common';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { catchError, of, switchMap, timer } from 'rxjs';

import { AuthService } from './core/services/auth.service';
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
  private readonly destroyRef = inject(DestroyRef);
  protected unreadMessages = 0;

  constructor() {
    this.user$.pipe(
      switchMap(user => user ? timer(0, 15000).pipe(
        switchMap(() => this.conversations.unreadCount().pipe(
          catchError(() => of({ count: this.unreadMessages }))
        ))
      ) : of({ count: 0 })),
      takeUntilDestroyed(this.destroyRef)
    ).subscribe(result => this.unreadMessages = result.count);
  }

  logout(): void {
    this.authService.logout();
  }
}
