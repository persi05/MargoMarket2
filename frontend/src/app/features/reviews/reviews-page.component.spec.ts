import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { ReviewsPageComponent } from './reviews-page.component';
import { environment } from '../../../environments/environment';

const pending = [{confirmationId: 4, sellerId: 7, sellerDisplayName: 'Rycerz', itemName: 'Miecz testowy',
  confirmedAt: '2026-11-20T10:00:00Z', reviewableUntil: '2026-12-20T10:00:00Z'}];

describe('Reviews page', () => {
  beforeEach(() => TestBed.configureTestingModule({imports: [ReviewsPageComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function render() {
    const fixture = TestBed.createComponent(ReviewsPageComponent);
    const http = TestBed.inject(HttpTestingController);
    http.expectOne(environment.apiUrl + '/reviews/pending').flush(pending);
    fixture.detectChanges();
    return {fixture, http, el: fixture.nativeElement as HTMLElement};
  }

  it('requires a rating before sending', () => {
    const {fixture, http, el} = render();
    expect(el.textContent).toContain('Miecz testowy');
    expect(el.querySelector('.pending a')!.getAttribute('href')).toBe('/u/7/profil');
    (el.querySelector('.actions button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('Wybierz ocenę');
    http.expectNone(environment.apiUrl + '/reviews');
  });

  it('publishes the review and removes the exchange from the list', () => {
    const {fixture, http, el} = render();
    (el.querySelectorAll('[role=radio]')[3] as HTMLButtonElement).click();
    const textarea = el.querySelector('textarea')!;
    textarea.value = '  Polecam  ';
    textarea.dispatchEvent(new Event('input'));
    fixture.detectChanges();
    expect(el.querySelector('[role=radio][aria-checked=true]')!.getAttribute('aria-label')).toContain('4 / 5');
    (el.querySelector('.actions button') as HTMLButtonElement).click();
    const request = http.expectOne(environment.apiUrl + '/reviews');
    expect(request.request.body).toEqual({confirmationId: 4, rating: 4, comment: 'Polecam'});
    request.flush({id: 1});
    fixture.detectChanges();
    expect(el.querySelector('.notice')!.textContent).toContain('Rycerz');
    expect(el.textContent).toContain('Nie masz wymian czekających na opinię');
  });

  it('shows the server message when the review is rejected', () => {
    const {fixture, http, el} = render();
    (el.querySelectorAll('[role=radio]')[0] as HTMLButtonElement).click();
    (el.querySelector('.actions button') as HTMLButtonElement).click();
    http.expectOne(environment.apiUrl + '/reviews').flush({message: 'Potwierdzenie tej wymiany zostało unieważnione.'}, {status: 400, statusText: 'Bad Request'});
    fixture.detectChanges();
    expect(el.querySelector('[role=alert]')!.textContent).toContain('unieważnione');
    expect(el.textContent).toContain('Miecz testowy');
  });
});
