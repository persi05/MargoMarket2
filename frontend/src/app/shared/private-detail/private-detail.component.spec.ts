import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { describe, expect, it } from 'vitest';
import { PrivateDetailComponent } from './private-detail.component';

function render(value: string | null, showLoginLink = true) {
  TestBed.configureTestingModule({ providers: [provideRouter([])] });
  const fixture = TestBed.createComponent(PrivateDetailComponent);
  fixture.componentInstance.value = value;
  fixture.componentInstance.showLoginLink = showLoginLink;
  fixture.detectChanges();
  return fixture.nativeElement as HTMLElement;
}

describe('private detail', () => {
  it('shows the value when it is visible to the viewer', () => {
    const el = render('150 zł');
    expect(el.textContent?.trim()).toBe('150 zł');
    expect(el.querySelector('a')).toBeNull();
  });

  it('masks a hidden value and links to the login page', () => {
    const el = render(null);
    expect(el.textContent).not.toContain('zł');
    expect(el.querySelector('a')?.getAttribute('href')).toContain('/auth');
  });

  it('describes the hidden value for screen readers when there is no login link', () => {
    const el = render(null, false);
    expect(el.querySelector('a')).toBeNull();
    expect(el.querySelector('.placeholder')?.getAttribute('aria-label')).toBe('Dostępne po zalogowaniu');
  });
});
