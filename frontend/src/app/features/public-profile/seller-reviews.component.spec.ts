import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { SellerReviewsComponent, reviewNoun } from './seller-reviews.component';
import { environment } from '../../../environments/environment';

const url = environment.apiUrl + '/profiles/7/reviews?page=1';
const data = {
  summary: {count: 2, average: 4.5, positivePercent: 100, distribution: [0, 0, 0, 1, 1]},
  reviews: {content: [
    {id: 11, rating: 5, comment: 'Szybko i uczciwie', buyerId: 3, buyerDisplayName: 'Kupiec', itemName: 'Miecz', createdAt: '2026-11-21T10:00:00Z'},
    {id: 12, rating: 4, comment: null, buyerId: 4, buyerDisplayName: 'Mag', itemName: 'Tarcza', createdAt: '2026-11-20T10:00:00Z'}
  ], page: 1, size: 10, totalElements: 2, totalPages: 1, first: true, last: true}
};

describe('Seller reviews', () => {
  beforeEach(() => TestBed.configureTestingModule({imports: [SellerReviewsComponent], providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])]}));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  function render(admin = false) {
    const fixture = TestBed.createComponent(SellerReviewsComponent);
    fixture.componentRef.setInput('sellerId', 7);
    fixture.componentRef.setInput('admin', admin);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    return {fixture, http, el: fixture.nativeElement as HTMLElement};
  }

  it('shows the average, distribution and reviews', () => {
    const {fixture, http, el} = render();
    http.expectOne(url).flush(data);
    fixture.detectChanges();
    expect(el.querySelector('.score strong')!.textContent).toBe('4,5');
    expect(el.querySelector('.score')!.textContent).toContain('2 opinie · 100% pozytywnych');
    expect(el.querySelectorAll('.review').length).toBe(2);
    expect(el.textContent).toContain('Szybko i uczciwie');
    expect(el.querySelector('.review a')!.getAttribute('href')).toBe('/u/3/profil');
    expect(el.querySelector('.link-button')).toBeNull();
  });

  it('says when a seller has no reviews yet', () => {
    const {fixture, http, el} = render();
    http.expectOne(url).flush({summary: {count: 0, average: null, positivePercent: null, distribution: [0, 0, 0, 0, 0]},
      reviews: {content: [], page: 1, size: 10, totalElements: 0, totalPages: 0, first: true, last: true}});
    fixture.detectChanges();
    expect(el.textContent).toContain('nie ma jeszcze opinii');
    expect(el.textContent).toContain('potwierdzonych przez pośrednika');
  });

  it('lets an admin hide a review with a reason', () => {
    const {fixture, http, el} = render(true);
    http.expectOne(url).flush(data);
    fixture.detectChanges();
    (el.querySelector('.link-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    (el.querySelector('.hide-form .mm-button') as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(el.querySelector('.hide-form [role=alert]')!.textContent).toContain('uzasadnienie');
    const input = el.querySelector('.hide-form input') as HTMLInputElement;
    input.value = 'Spam';
    input.dispatchEvent(new Event('input'));
    (el.querySelector('.hide-form .mm-button') as HTMLButtonElement).click();
    const hide = http.expectOne(environment.apiUrl + '/admin/reviews/11/hide');
    expect(hide.request.body).toEqual({reason: 'Spam'});
    hide.flush(null);
    http.expectOne(url).flush(data);
  });

  it('uses Polish plural forms', () => {
    expect([1, 2, 4, 5, 12, 22, 25].map(reviewNoun)).toEqual(['opinia', 'opinie', 'opinie', 'opinii', 'opinii', 'opinie', 'opinii']);
  });
});
