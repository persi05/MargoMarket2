import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { BehaviorSubject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { AuthService } from '../../core/services/auth.service';
import { ItemPreviewComponent } from './item-preview.component';

describe('shared item preview', () => {
  const auth = { isLoggedIn: true, user$: new BehaviorSubject<unknown>({ id: 1 }) };
  beforeEach(() => {
    auth.isLoggedIn = true;
    TestBed.configureTestingModule({
      imports: [ItemPreviewComponent],
      providers: [provideRouter([]), { provide: AuthService, useValue: auth }],
    });
  });
  afterEach(() => TestBed.resetTestingModule());
  function create() {
    const fixture = TestBed.createComponent(ItemPreviewComponent);
    fixture.componentRef.setInput('listing', {
      id: 1,
      itemName: 'Miecz',
      price: 100,
      currency: { name: 'PLN' },
      server: { name: 'Syberia' },
      itemType: { name: 'Jednoręczne' },
      rarity: { name: 'Legendarny' },
      sellerId: 2,
      sellerDisplayName: 'Sprzedawca',
      level: 100,
      enhancementLevel: 0,
      bound: false,
      itemStats: 'lvl=100;rarity=legendary',
    });
    fixture.detectChanges();
    fixture.nativeElement.querySelector('.item-frame').dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    return fixture;
  }
  it('pins the same preview, moves it with the keyboard and closes it', () => {
    const fixture = create();
    const component = fixture.componentInstance as any;
    fixture.nativeElement.querySelector('.preview-pin').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('[role="dialog"]')).toBeTruthy();
    component.x = 30;
    const handle = fixture.nativeElement.querySelector('.preview-tool');
    handle.dispatchEvent(new KeyboardEvent('keydown', { key: 'ArrowRight', bubbles: true }));
    fixture.detectChanges();
    expect(component.x).toBe(54);
    fixture.nativeElement.querySelector('.preview-close').click();
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.item-tooltip')).toBeNull();
    fixture.destroy();
  });
  it('closes pinned private data on logout and masks the price when reopened', () => {
    const fixture = create();
    fixture.nativeElement.querySelector('.preview-pin').click();
    fixture.detectChanges();
    auth.isLoggedIn = false;
    auth.user$.next(null);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.item-tooltip')).toBeNull();
    fixture.nativeElement.querySelector('.item-frame').dispatchEvent(new MouseEvent('mouseenter'));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.masked')).toBeTruthy();
    fixture.destroy();
  });
});
