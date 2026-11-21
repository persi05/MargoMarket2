import { Component, input } from '@angular/core';

/**
 * Shared page heading: optional eyebrow, title, optional lead text.
 * Actions placed with the `pageActions` attribute sit on the right; other projected content goes under the title.
 */
@Component({
  selector: 'mm-page-header',
  standalone: true,
  template: `
    <header class="page-header">
      <ng-content select="[pageMedia]" />
      <div class="page-header-text">
        @if (eyebrow()) { <p class="page-eyebrow">{{ eyebrow() }}</p> }
        <h1 class="page-title">{{ title() }}</h1>
        @if (lead()) { <p class="page-lead">{{ lead() }}</p> }
      </div>
      <div class="page-header-actions"><ng-content select="[pageActions]" /></div>
      <div class="page-header-extra"><ng-content /></div>
    </header>
  `,
  styles: [`
    :host { display: block; margin-bottom: 24px; }
    :host ::ng-deep .page-header:has(> [pagemedia]) { align-items: center; }
    .page-header { display: flex; flex-wrap: wrap; align-items: flex-end; gap: 12px 20px; }
    .page-header-text { flex: 1 1 320px; min-width: 0; }
    .page-eyebrow { margin: 0 0 6px; color: var(--color-primary); font-size: 12px; font-weight: 800; letter-spacing: .12em; text-transform: uppercase; }
    .page-title { margin: 0; color: white; font-size: clamp(30px, 4vw, 44px); line-height: 1.1; overflow-wrap: anywhere; }
    .page-lead { margin: 10px 0 0; max-width: 75ch; color: var(--color-text-muted); line-height: 1.6; }
    .page-header-actions { display: flex; flex-wrap: wrap; gap: 10px; }
    .page-header-actions:empty, .page-header-extra:empty { display: none; }
    .page-header-extra { flex: 1 0 100%; }
    @media (max-width: 620px) {
      :host { margin-bottom: 18px; }
      .page-header-actions { flex: 1 0 100%; }
    }
  `]
})
export class PageHeaderComponent {
  readonly eyebrow = input('');
  readonly title = input.required<string>();
  readonly lead = input('');
}
