import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { CatalogAdminComponent } from './catalog-admin.component';

describe('catalog admin', () => {
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [CatalogAdminComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    TestBed.resetTestingModule();
  });
  it('starts an import and disables the button while it runs', async () => {
    const fixture = TestBed.createComponent(CatalogAdminComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 20));
    http.expectOne('/api/admin/catalog').flush({ status: 'IDLE', pages: 0 });
    fixture.detectChanges();
    const button: HTMLButtonElement = fixture.nativeElement.querySelector('button');
    expect(button.disabled).toBe(false);
    button.click();
    const request = http.expectOne('/api/admin/catalog');
    expect(request.request.method).toBe('POST');
    request.flush({ status: 'RUNNING', pages: 2 });
    fixture.detectChanges();
    expect(button.disabled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Pobrane strony: 2');
    fixture.destroy();
    http.verify();
  });
  it('shows failure and allows retry', async () => {
    const fixture = TestBed.createComponent(CatalogAdminComponent);
    fixture.detectChanges();
    await new Promise((resolve) => setTimeout(resolve, 20));
    http
      .expectOne('/api/admin/catalog')
      .flush({ status: 'FAILED', error: 'Dane zostały zachowane.' });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Dane zostały zachowane.');
    expect(fixture.nativeElement.querySelector('button').disabled).toBe(false);
    fixture.destroy();
    http.verify();
  });
  it('polls only while an import is running', async () => {
    vi.useFakeTimers();
    const fixture = TestBed.createComponent(CatalogAdminComponent);
    try {
      fixture.detectChanges();
      await vi.advanceTimersByTimeAsync(0);
      http.expectOne('/api/admin/catalog').flush({ status: 'IDLE', pages: 0 });
      await vi.advanceTimersByTimeAsync(9000);
      http.expectNone('/api/admin/catalog');
      (fixture.componentInstance as any).start();
      http.expectOne('/api/admin/catalog').flush({ status: 'RUNNING', pages: 0 });
      await vi.advanceTimersByTimeAsync(3000);
      http.expectOne('/api/admin/catalog').flush({ status: 'SUCCEEDED', pages: 23 });
      await vi.advanceTimersByTimeAsync(6000);
      http.expectNone('/api/admin/catalog');
      http.verify();
    } finally {
      fixture.destroy();
      vi.useRealTimers();
    }
  });
});
