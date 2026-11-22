import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { of, throwError } from 'rxjs';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { GoldFormComponent } from './gold-form.component';
describe('gold form validation', () => {
  afterEach(() => TestBed.resetTestingModule());
  function setup() {
    const service = {
      goldConfig: () => of({ enabled: true }),
      createGold: vi.fn(() => of({ id: 9 })),
    };
    TestBed.configureTestingModule({
      imports: [GoldFormComponent],
      providers: [
        provideRouter([]),
        { provide: ListingService, useValue: service },
        {
          provide: DictionaryService,
          useValue: { getAll: () => of({ servers: [{ id: 1, name: 'Telawel' }] }) },
        },
      ],
    });
    const fixture = TestBed.createComponent(GoldFormComponent);
    const component = fixture.componentInstance as any;
    fixture.detectChanges();
    return { fixture, component, service };
  }
  it('shows a separate error for every missing field without sending a request', async () => {
    const { fixture, component, service } = setup();
    await fixture.whenStable();
    fixture.nativeElement
      .querySelector('form')
      .dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    await fixture.whenStable();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('#gold-world-error').textContent).toContain(
      'Wybierz świat',
    );
    expect(fixture.nativeElement.querySelector('#gold-amount-error').textContent).toContain(
      'Podaj ilość',
    );
    expect(fixture.nativeElement.querySelector('#gold-rate-error').textContent).toContain(
      'Podaj kurs',
    );
    expect(service.createGold).not.toHaveBeenCalled();
    expect(fixture.nativeElement.querySelector('textarea')).toBeNull();
  });
  it('accepts the amount and rate shown in the reported example', () => {
    const { component, service } = setup();
    Object.assign(component.form, { serverId: 1, amount: '1500m', rate: '15m' });
    component.submit();
    expect(service.createGold).toHaveBeenCalledWith(
      expect.objectContaining({ serverId: 1, goldAmount: 1500000000, rateGoldUnits: 15000000 }),
    );
  });
  it('attaches invalid package totals to the rate field', () => {
    const { component, service } = setup();
    Object.assign(component.form, { serverId: 1, amount: '2g', rate: '0.000001' });
    component.submit();
    expect(component.fieldError('rate')).toContain('cena pakietu');
    expect(service.createGold).not.toHaveBeenCalled();
  });
  it('maps backend field errors and clears them when the field changes', () => {
    const { component, service } = setup();
    service.createGold.mockReturnValue(
      throwError(() => ({ error: { errors: { rateGoldUnits: 'Niepoprawny kurs' } } })) as any,
    );
    Object.assign(component.form, { serverId: 1, amount: '1500m', rate: '15m' });
    component.submit();
    expect(component.fieldError('rate')).toBe('Niepoprawny kurs');
    component.changed('rate');
    expect(component.fieldError('rate')).toBe('');
  });
});
