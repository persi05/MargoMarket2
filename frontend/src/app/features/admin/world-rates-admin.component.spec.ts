import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, describe, expect, it } from 'vitest';
import { WorldRates } from '../../core/models/api.models';
import { WorldRatesAdminComponent } from './world-rates-admin.component';

const luvia: WorldRates = {
  id: 1, name: 'Luvia', worldType: 'ISOLATED', goldPerPln: 18_000_000,
  upgradeMillionPriceGrosze: 15_000, essencePriceGrosze: 200, ratesUpdatedAt: null
};

describe('world rates admin', () => {
  afterEach(() => TestBed.resetTestingModule());

  async function render() {
    TestBed.configureTestingModule({ imports: [WorldRatesAdminComponent], providers: [provideHttpClient(), provideHttpClientTesting()] });
    const fixture = TestBed.createComponent(WorldRatesAdminComponent);
    fixture.detectChanges();
    const http = TestBed.inject(HttpTestingController);
    http.expectOne('/api/admin/worlds').flush([luvia]);
    http.expectOne('/api/admin/worlds/settings').flush({ scalesHundredPriceGrosze: 150 });
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return { fixture, http, page: fixture.componentInstance as any };
  }

  it('shows rates in the units admins think in', async () => {
    const { fixture } = await render();
    const inputs = [...fixture.nativeElement.querySelectorAll('tbody tr:first-child input')] as HTMLInputElement[];
    expect(scalesInput(fixture)).toBe('1,5');
    expect(fixture.nativeElement.textContent).toContain('Luvia');
    expect(inputs.map(input => input.value)).toEqual(['18', '150', '2']);
  });

  it('saves edited rates in minor units', async () => {
    const { http, page } = await render();
    const row = page.rows()[0];
    row.upgrade = '120,5';
    row.essence = '';
    page.save(row);
    const request = http.expectOne('/api/admin/worlds/1');
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ worldType: 'ISOLATED', goldPerPln: 18_000_000, upgradeMillionPriceGrosze: 12_050, essencePriceGrosze: null });
    request.flush({ ...luvia, upgradeMillionPriceGrosze: 12_050, essencePriceGrosze: null });
    expect(page.rows()[0].message).toBe('Zapisano.');
  });

  it('adds a new world and rejects invalid numbers', async () => {
    const { http, page } = await render();
    const fresh = page.rows()[1];
    fresh.name = 'Hades';
    fresh.gold = 'dużo';
    page.save(fresh);
    http.expectNone('/api/admin/worlds');
    expect(page.rows()[1].message).toContain('Wpisz liczby');

    fresh.gold = '30';
    page.save(fresh);
    const request = http.expectOne(r => r.method === 'POST' && r.url === '/api/admin/worlds');
    expect(request.request.body).toEqual({ name: 'Hades', worldType: 'PRIVATE', goldPerPln: 30_000_000, upgradeMillionPriceGrosze: null, essencePriceGrosze: null });
    request.flush({ ...luvia, id: 2, name: 'Hades', worldType: 'PRIVATE', goldPerPln: 30_000_000 });
    expect(page.rows().map((r: any) => r.name)).toEqual(['Hades', 'Luvia', '']);
  });

  it('saves the scale price and rejects invalid values', async () => {
    const { http, page } = await render();
    page.scales = 'tanio';
    page.saveScales();
    http.expectNone('/api/admin/worlds/settings');
    expect(page.scalesMessage()).toContain('Wpisz liczbę');

    page.scales = '2';
    page.saveScales();
    const request = http.expectOne(r => r.method === 'PUT' && r.url === '/api/admin/worlds/settings');
    expect(request.request.body).toEqual({ scalesHundredPriceGrosze: 200 });
    request.flush({ scalesHundredPriceGrosze: 200 });
    expect(page.scalesMessage()).toBe('Zapisano.');
  });
});

function scalesInput(fixture: any): string {
  return (fixture.nativeElement.querySelector('#scales-price') as HTMLInputElement).value;
}
