import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { Router, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject, of } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { AppComponent } from './app.component';
import { AuthService } from './core/services/auth.service';
import { ChatRealtimeService } from './core/services/chat-realtime.service';
import { ConversationService } from './core/services/conversation.service';
import { Component } from '@angular/core';

@Component({ template: '' }) class EmptyPage {}

describe('app header menu', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    TestBed.configureTestingModule({
      imports: [AppComponent],
      providers: [
        provideRouter([{ path: 'items', component: EmptyPage }, { path: 'prices', component: EmptyPage }]),
        provideHttpClient(), provideHttpClientTesting(),
        { provide: AuthService, useValue: { user$: new BehaviorSubject(null), currentUser: null, logout: () => {} } },
        { provide: ChatRealtimeService, useValue: { events$: new Subject(), connected: false, connect: () => {}, disconnect: () => {} } },
        { provide: ConversationService, useValue: { unreadCount: () => of({ count: 0 }) } }
      ]
    });
    const fixture = TestBed.createComponent(AppComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    const element = fixture.nativeElement as HTMLElement;
    const toggle = element.querySelector<HTMLButtonElement>('.menu-toggle')!;
    const nav = element.querySelector('#main-nav')!;
    return { fixture, element, toggle, nav };
  }

  it('opens and closes the navigation with the menu button', async () => {
    const { fixture, toggle, nav } = await render();
    expect(toggle.getAttribute('aria-expanded')).toBe('false');
    toggle.click();
    fixture.detectChanges();
    expect(toggle.getAttribute('aria-expanded')).toBe('true');
    expect(toggle.getAttribute('aria-label')).toBe('Zamknij menu');
    expect(nav.classList).toContain('open');
    toggle.click();
    fixture.detectChanges();
    expect(nav.classList).not.toContain('open');
  });

  it('closes the navigation after going to another page', async () => {
    const { fixture, toggle, nav } = await render();
    toggle.click();
    fixture.detectChanges();
    await TestBed.inject(Router).navigateByUrl('/prices');
    fixture.detectChanges();
    expect(nav.classList).not.toContain('open');
  });
});
