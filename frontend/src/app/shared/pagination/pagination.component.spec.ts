import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PageResponse } from '../../core/models/api.models';
import { PaginationComponent } from './pagination.component';

function render(page: Partial<PageResponse<unknown>>, loading = false) {
  const fixture = TestBed.createComponent(PaginationComponent);
  fixture.componentRef.setInput('page', { content: [], page: 2, size: 20, totalElements: 60, totalPages: 3, first: false, last: false, ...page });
  fixture.componentRef.setInput('loading', loading);
  fixture.detectChanges();
  const emitted: number[] = [];
  fixture.componentInstance.pageChange.subscribe(p => emitted.push(p));
  const el = fixture.nativeElement as HTMLElement;
  return { el, emitted, buttons: Array.from(el.querySelectorAll('button')) };
}

describe('pagination', () => {
  it('hides itself when everything fits on one page', () => {
    expect(render({ totalPages: 1 }).el.querySelector('nav')).toBeNull();
  });

  it('moves to the previous and next page', () => {
    const { el, emitted, buttons } = render({});
    expect(el.textContent).toContain('Strona 2 z 3');
    buttons[0].click();
    buttons[1].click();
    expect(emitted).toEqual([1, 3]);
  });

  it('disables the buttons at the edges and while loading', () => {
    expect(render({ page: 1, first: true }).buttons[0].disabled).toBe(true);
    expect(render({ page: 3, last: true }).buttons[1].disabled).toBe(true);
    expect(render({}, true).buttons.every(b => b.disabled)).toBe(true);
  });
});
