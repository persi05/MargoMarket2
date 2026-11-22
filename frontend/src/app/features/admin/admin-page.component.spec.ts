import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AdminPageComponent } from './admin-page.component';

describe('admin tab isolation', () => {
  let http: HttpTestingController;
  const empty = {
    content: [],
    page: 1,
    size: 20,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [AdminPageComponent],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    TestBed.resetTestingModule();
  });
  it('renders and loads exchange confirmations and auction moderation only in their own tabs', () => {
    const fixture = TestBed.createComponent(AdminPageComponent);
    fixture.detectChanges();
    http.expectOne('/api/admin/stats').flush({ listings: 12, users: 4 });
    http.expectOne((r) => r.url === '/api/admin/listings').flush(empty);
    http
      .expectOne('/api/dictionaries')
      .flush({ servers: [], itemTypes: [], currencies: [], rarities: [] });
    fixture.detectChanges();
    const root: HTMLElement = fixture.nativeElement;
    const expectHidden = () => {
      expect(root.querySelector('mm-auction-moderation')).toBeNull();
      expect(root.querySelector('mm-exchange-confirmations')).toBeNull();
    };
    const click = (text: string) => {
      const button = Array.from(root.querySelectorAll('nav.tabs button')).find(
        (b) => b.textContent?.trim() === text,
      ) as HTMLButtonElement;
      button.click();
      fixture.detectChanges();
    };
    expectHidden();
    http.expectNone(
      (r) =>
        r.url.includes('exchange-confirmations') ||
        r.url.includes('/admin/auctions') ||
        r.url === '/api/admin/users',
    );
    click('Moderacja licytacji');
    expect(root.querySelectorAll('mm-auction-moderation').length).toBe(1);
    expect(root.querySelector('mm-exchange-confirmations')).toBeNull();
    http.expectOne((r) => r.url === '/api/admin/auctions/register/reports').flush(empty);
    http.expectNone(
      (r) =>
        r.url === '/api/admin/listings' ||
        r.url === '/api/admin/users' ||
        r.url === '/api/admin/stats',
    );
    click('Potwierdzanie wymian');
    expect(root.querySelectorAll('mm-exchange-confirmations').length).toBe(1);
    expect(root.querySelector('mm-auction-moderation')).toBeNull();
    http.expectOne('/api/admin/exchange-confirmations?page=1').flush(empty);
    click('Użytkownicy');
    expectHidden();
    http.expectOne((r) => r.url === '/api/admin/users').flush(empty);
    http.expectNone(
      (r) =>
        r.url === '/api/admin/listings' ||
        r.url.includes('/admin/auctions') ||
        r.url.includes('exchange-confirmations'),
    );
    fixture.destroy();
  });
});
