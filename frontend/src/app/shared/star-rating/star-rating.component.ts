import { Component, input, output } from '@angular/core';

export const RATING_LABELS = ['Bardzo źle', 'Źle', 'Przeciętnie', 'Dobrze', 'Bardzo dobrze'];

/** Five stars. Read-only by default; with `editable` it is a radio group the keyboard can use. */
@Component({
  selector: 'mm-star-rating',
  standalone: true,
  template: `
    @if (editable()) {
      <div class="stars editable" role="radiogroup" [attr.aria-label]="label()">
        @for (star of stars; track star) {
          <button type="button" role="radio" [attr.aria-checked]="value() === star" [attr.aria-label]="star + ' / 5 – ' + labels[star - 1]"
            [class.on]="star <= value()" (click)="changed.emit(star)" [disabled]="disabled()">★</button>
        }
      </div>
    } @else {
      <span class="stars" role="img" [attr.aria-label]="'Ocena ' + value() + ' na 5'">
        @for (star of stars; track star) { <span aria-hidden="true" [class.on]="star <= roundedValue()">★</span> }
      </span>
    }
  `,
  styles: [`
    :host { display: inline-block; }
    .stars { display: inline-flex; gap: 2px; color: rgba(185, 173, 159, .35); font-size: 1.05em; line-height: 1; }
    .on { color: #facc15; }
    .editable button { padding: 4px; border: 0; background: none; color: rgba(185, 173, 159, .4); font-size: 1.8rem; line-height: 1; cursor: pointer; }
    .editable button.on { color: #facc15; }
    .editable button:focus-visible { outline: 2px solid var(--color-primary); outline-offset: 1px; border-radius: 4px; }
    .editable button:disabled { cursor: not-allowed; opacity: .6; }
  `]
})
export class StarRatingComponent {
  readonly value = input(0);
  readonly editable = input(false);
  readonly disabled = input(false);
  readonly label = input('Ocena');
  readonly changed = output<number>();
  protected readonly stars = [1, 2, 3, 4, 5];
  protected readonly labels = RATING_LABELS;
  protected roundedValue(): number { return Math.round(this.value()); }
}
