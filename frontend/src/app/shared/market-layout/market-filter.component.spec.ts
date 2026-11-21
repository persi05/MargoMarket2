import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { MarketFilterComponent } from './market-filter.component';

@Component({
  imports: [MarketFilterComponent],
  template: `<mm-market-filter><form (submit)="$event.preventDefault()"><button type="submit">Filtruj</button></form></mm-market-filter>`
})
class Host {}

describe('market filter', () => {
  afterEach(() => TestBed.resetTestingModule());

  function render() {
    const fixture = TestBed.createComponent(Host);
    fixture.detectChanges();
    const element = fixture.nativeElement as HTMLElement;
    return { fixture, toggle: element.querySelector<HTMLButtonElement>('.filter-toggle')!, body: element.querySelector('.filter-body')!, element };
  }

  it('starts folded and unfolds with the Filtry button', () => {
    const { fixture, toggle, body } = render();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    expect(body.classList).toContain('collapsed');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(body.classList).not.toContain('collapsed');
  });

  it('folds again after filtering on a narrow screen', () => {
    const { fixture, toggle, body, element } = render();
    toggle.click();
    fixture.detectChanges();
    const width = window.innerWidth;
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: 390 });
    element.querySelector<HTMLButtonElement>('button[type=submit]')!.click();
    fixture.detectChanges();
    Object.defineProperty(window, 'innerWidth', { configurable: true, value: width });
    expect(body.classList).toContain('collapsed');
  });
});
