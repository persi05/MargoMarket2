import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PageHeaderComponent } from './page-header.component';

@Component({
  standalone: true,
  imports: [PageHeaderComponent],
  template: `<mm-page-header eyebrow="Konto" title="Profil" lead="Opis strony"><a pageActions href="/new">Akcja</a><p>Pod tytułem</p></mm-page-header>`
})
class HostComponent {}

describe('page header', () => {
  it('shows the eyebrow, title, lead, actions and extra content in their slots', () => {
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.page-eyebrow')?.textContent).toBe('Konto');
    expect(el.querySelector('h1')?.textContent).toBe('Profil');
    expect(el.querySelector('.page-lead')?.textContent).toBe('Opis strony');
    expect(el.querySelector('.page-header-actions a')?.textContent).toBe('Akcja');
    expect(el.querySelector('.page-header-extra p')?.textContent).toBe('Pod tytułem');
  });

  it('leaves out the eyebrow and lead when they are not given', () => {
    TestBed.overrideComponent(HostComponent, { set: { template: '<mm-page-header title="Wiadomości" />' } });
    const fixture = TestBed.createComponent(HostComponent);
    fixture.detectChanges();
    const el = fixture.nativeElement as HTMLElement;
    expect(el.querySelector('.page-eyebrow')).toBeNull();
    expect(el.querySelector('.page-lead')).toBeNull();
  });
});
