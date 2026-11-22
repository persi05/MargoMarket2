import { Component, ElementRef, HostListener, ViewChild, ViewEncapsulation } from '@angular/core';
@Component({
  selector: 'mm-market-filter',
  standalone: true,
  encapsulation: ViewEncapsulation.None,
  template: `
    <div
      class="market-filter-panel"
      #filterPanel
      [class.is-dragging]="filterDragging"
      [class.has-custom-position]="filterPosition !== null"
      [style.left.px]="filterPosition?.x"
      [style.top.px]="filterPosition?.y"
      (pointermove)="moveFilter($event)"
      (pointerup)="stopFilterDrag($event)"
      (pointercancel)="stopFilterDrag($event)"
      (lostpointercapture)="stopFilterDrag($event)"
    >
      <button
        type="button"
        class="filter-toggle"
        [attr.aria-expanded]="expanded"
        aria-controls="market-filter-body"
        (click)="expanded = !expanded"
      >
        <svg
          viewBox="0 0 24 24"
          width="18"
          height="18"
          fill="none"
          stroke="currentColor"
          stroke-width="1.9"
          stroke-linecap="round"
          aria-hidden="true"
        >
          <path d="M4 6h16M7 12h10M10 18h4" />
        </svg>
        <span>Filtry</span>
        <svg class="chevron" viewBox="0 0 16 16" width="16" height="16" aria-hidden="true">
          <path
            d="m4 6 4 4 4-4"
            fill="none"
            stroke="currentColor"
            stroke-width="1.7"
            stroke-linecap="round"
            stroke-linejoin="round"
          />
        </svg>
      </button>
      <div class="filter-top" (pointerdown)="startFilterDrag($event)">
        <h2>Filtrowanie</h2>
        <button
          type="button"
          class="panel-tool reset-position"
          aria-label="Przywróć położenie panelu filtrowania"
          title="Przywróć położenie"
          (click)="resetFilterPosition()"
        >
          <svg
            viewBox="0 0 20 20"
            width="18"
            height="18"
            fill="none"
            aria-hidden="true"
            focusable="false"
          >
            <path
              d="M4 9a6 6 0 1 1 1.7 4.2M4 4v5h5"
              stroke="currentColor"
              stroke-width="1.8"
              stroke-linecap="round"
              stroke-linejoin="round"
            />
          </svg>
        </button>
        <button
          type="button"
          class="panel-tool drag-handle"
          aria-label="Przesuń panel filtrowania"
          title="Przeciągnij panel filtrowania"
          (keydown)="moveFilterWithKeyboard($event)"
        >
          <svg viewBox="0 0 16 20" width="16" height="20" aria-hidden="true" focusable="false">
            <circle cx="5" cy="4" r="1.6" />
            <circle cx="11" cy="4" r="1.6" />
            <circle cx="5" cy="10" r="1.6" />
            <circle cx="11" cy="10" r="1.6" />
            <circle cx="5" cy="16" r="1.6" />
            <circle cx="11" cy="16" r="1.6" />
          </svg>
        </button>
      </div>

      <div
        class="filter-body"
        id="market-filter-body"
        [class.collapsed]="!expanded"
        (submit)="collapseOnNarrowScreen()"
      >
        <ng-content />
      </div>
    </div>
  `,
  styleUrl: './market-filter.component.css',
})
export class MarketFilterComponent {
  /** On phones and narrow tablets the filter starts folded so the offers are visible first; wide screens always show it. */
  protected expanded = false;
  protected collapseOnNarrowScreen(): void {
    if (window.innerWidth <= 1100) this.expanded = false;
  }
  @ViewChild('filterPanel') private filterPanel?: ElementRef<HTMLElement>;
  protected filterPosition: { x: number; y: number } | null = null;
  protected filterDragging = false;
  private filterDragState: {
    pointerId: number;
    offsetX: number;
    offsetY: number;
    width: number;
    height: number;
  } | null = null;
  protected startFilterDrag(event: PointerEvent): void {
    if (
      !event.isPrimary ||
      event.button !== 0 ||
      window.innerWidth <= 1100 ||
      (event.target as HTMLElement).closest('.reset-position')
    ) {
      return;
    }

    const panel = this.filterPanel?.nativeElement;
    if (!panel) {
      return;
    }

    const bounds = panel.getBoundingClientRect();
    this.filterPosition = { x: bounds.left, y: bounds.top };
    this.filterDragState = {
      pointerId: event.pointerId,
      offsetX: event.clientX - bounds.left,
      offsetY: event.clientY - bounds.top,
      width: bounds.width,
      height: bounds.height,
    };
    this.filterDragging = true;
    panel.setPointerCapture(event.pointerId);
    event.preventDefault();
  }

  protected resetFilterPosition(): void {
    this.filterPosition = null;
    this.filterDragState = null;
    this.filterDragging = false;
  }

  protected moveFilter(event: PointerEvent): void {
    const drag = this.filterDragState;
    if (!drag || event.pointerId !== drag.pointerId) {
      return;
    }

    this.filterPosition = this.clampFilterPosition(
      event.clientX - drag.offsetX,
      event.clientY - drag.offsetY,
      drag.width,
      drag.height,
    );
  }

  protected stopFilterDrag(event: PointerEvent): void {
    if (event.pointerId === this.filterDragState?.pointerId) {
      this.filterDragState = null;
      this.filterDragging = false;
    }
  }

  protected moveFilterWithKeyboard(event: KeyboardEvent): void {
    if (window.innerWidth <= 1100) {
      return;
    }

    if (event.key === 'Home') {
      this.filterPosition = null;
      event.preventDefault();
      return;
    }

    const shifts: Record<string, [number, number]> = {
      ArrowLeft: [-24, 0],
      ArrowRight: [24, 0],
      ArrowUp: [0, -24],
      ArrowDown: [0, 24],
    };
    const shift = shifts[event.key];
    const panel = this.filterPanel?.nativeElement;
    if (!shift || !panel) {
      return;
    }

    const bounds = panel.getBoundingClientRect();
    this.filterPosition = this.clampFilterPosition(
      bounds.left + shift[0],
      bounds.top + shift[1],
      bounds.width,
      bounds.height,
    );
    event.preventDefault();
  }

  private clampFilterPosition(x: number, y: number, width: number, height: number) {
    return {
      x: Math.min(Math.max(8, x), Math.max(8, window.innerWidth - width - 8)),
      y: Math.min(Math.max(80, y), Math.max(80, window.innerHeight - height - 8)),
    };
  }
  @HostListener('window:resize') protected resize() {
    if (window.innerWidth <= 1100) {
      this.resetFilterPosition();
      return;
    }
    if (this.filterPosition && this.filterPanel) {
      const bounds = this.filterPanel.nativeElement.getBoundingClientRect();
      this.filterPosition = this.clampFilterPosition(
        this.filterPosition.x,
        this.filterPosition.y,
        bounds.width,
        bounds.height,
      );
    }
  }
}
