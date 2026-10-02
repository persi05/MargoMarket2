import { Component, Input, inject } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

@Component({
  selector: 'mm-private-detail',
  standalone: true,
  imports: [RouterLink],
  template: `
    @if (value !== null) {
      {{ value }}
    } @else {
      <span class="placeholder">
        <span class="masked" aria-hidden="true">{{ compact ? '••••••••' : '•••••••••••' }}</span>
        <a routerLink="/auth" [queryParams]="{ returnUrl: router.url }">Zaloguj się</a>
      </span>
    }
  `,
  styles: `
    :host {
      display: inline-flex;
      flex-direction: column;
      align-items: flex-start;
      vertical-align: middle;
    }

    .placeholder {
      display: inline-flex;
      flex-direction: column;
      align-items: center;
    }

    .masked {
      display: block;
      width: 100%;
      color: var(--color-text-main);
      filter: blur(4px);
      pointer-events: none;
      text-align: center;
      user-select: none;
    }

    a {
      color: var(--color-primary);
      font-size: 11px;
      font-weight: 700;
      line-height: 1.2;
      white-space: nowrap;
    }
  `
})
export class PrivateDetailComponent {
  @Input() value: string | null = null;
  @Input() compact = false;
  protected readonly router = inject(Router);
}
