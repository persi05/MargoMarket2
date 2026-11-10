import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { ListingService } from './listing.service';
import { AuthService } from './auth.service';
import { environment } from '../../../environments/environment';
describe('Public marketplace cache', () => {
  let service: ListingService;
  let http: HttpTestingController;
  const auth = { isLoggedIn: false };
  const page = {
    content: [],
    page: 1,
    size: 50,
    totalElements: 0,
    totalPages: 0,
    first: true,
    last: true,
  };
  beforeEach(() => {
    auth.isLoggedIn = false;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: AuthService, useValue: auth },
      ],
    });
    service = TestBed.inject(ListingService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => {
    http.verify();
    vi.useRealTimers();
  });
  it('keeps separate caches for items and gold', () => {
    service.search().subscribe();
    http
      .expectOne(
        (r) => r.url === environment.apiUrl + '/listings' && r.params.get('publicOnly') === 'true',
      )
      .flush(page);
    service.searchGold().subscribe();
    http.expectOne((r) => r.url.endsWith('/gold')).flush(page);
    service.search().subscribe();
    service.searchGold().subscribe();
    http.expectNone(() => true);
  });
  it('expires and invalidates cached pages', () => {
    vi.useFakeTimers();
    service.search().subscribe();
    http.expectOne(() => true).flush(page);
    service.search().subscribe();
    http.expectNone(() => true);
    vi.advanceTimersByTime(30001);
    service.search().subscribe();
    http.expectOne(() => true).flush(page);
    service.invalidate();
    service.search().subscribe();
    http.expectOne(() => true).flush(page);
  });
  it('does not cache errors', () => {
    service.search().subscribe({ error: () => {} });
    http.expectOne(() => true).flush({}, { status: 500, statusText: 'Error' });
    service.search().subscribe();
    http.expectOne(() => true).flush(page);
  });
  it('does not revive an invalidated response', () => {
    service.search().subscribe();
    const first = http.expectOne(() => true);
    service.invalidate();
    first.flush(page);
    service.search().subscribe();
    http.expectOne(() => true).flush(page);
  });
  it('refreshes quote direction and quantity together with the price', () => {
    auth.isLoggedIn = true;
    let result: any;
    service.searchGold().subscribe((r) => (result = r));
    http
      .expectOne((r) => r.url.endsWith('/gold'))
      .flush({
        ...page,
        content: [
          {
            id: 7,
            listingKind: 'GOLD',
            goldAmount: 260000000,
            goldRateMode: 'M_PER_PLN',
            price: null,
            priceMinor: null,
            rateGoldUnits: null,
            ratePriceMinor: null,
            contact: null,
            status: 'active',
          },
        ],
      });
    http
      .expectOne((r) => r.url.endsWith('/private-details'))
      .flush([
        {
          id: 7,
          status: 'active',
          goldAmount: 1500000000,
          goldRateMode: 'PLN_PER_G',
          priceMinor: 3450,
          rateGoldUnits: 1000000000,
          ratePriceMinor: 2300,
          contact: '',
        },
      ]);
    expect(result.content[0].goldRateMode).toBe('PLN_PER_G');
    expect(result.content[0].goldAmount).toBe(1500000000);
    expect(result.content[0].priceMinor).toBe(3450);
  });
  it('does not keep private details in the public cache', () => {
    auth.isLoggedIn = true;
    const listing = { id: 3, status: 'active', price: null, priceMinor: null, contact: null };
    let result: any;
    service.search().subscribe((r) => (result = r));
    http
      .expectOne((r) => r.params.get('publicOnly') === 'true')
      .flush({ ...page, content: [listing] });
    http
      .expectOne((r) => r.url.endsWith('/private-details'))
      .flush([{ id: 3, status: 'active', price: 100, priceMinor: null, contact: 'secret' }]);
    expect(result.content[0].contact).toBe('secret');
    auth.isLoggedIn = false;
    service.search().subscribe((r) => (result = r));
    http.expectNone(() => true);
    expect(result.content[0].contact).toBeNull();
    expect(result.content[0].price).toBeNull();
  });
});
