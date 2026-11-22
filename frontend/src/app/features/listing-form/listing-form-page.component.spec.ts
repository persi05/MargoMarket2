import { TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { afterEach, describe, expect, it } from 'vitest';
import { DictionaryService } from '../../core/services/dictionary.service';
import { ListingService } from '../../core/services/listing.service';
import { AuctionService } from '../../core/services/auction.service';
import { ItemService } from '../../core/services/item.service';
import { ListingFormPageComponent } from './listing-form-page.component';
describe('auction form validation',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 function setup(){TestBed.configureTestingModule({imports:[ListingFormPageComponent],providers:[provideRouter([]),{provide:DictionaryService,useValue:{getAll:()=>of({servers:[],currencies:[],rarities:[],itemTypes:[]})}},{provide:ListingService,useValue:{}},{provide:AuctionService,useValue:{config:()=>of({enabled:true,plnEnabled:true})}},{provide:ItemService,useValue:{search:()=>of([])}}]});const component=TestBed.createComponent(ListingFormPageComponent).componentInstance as any;component.auctionMode=true;return component;}
 it('identifies missing price and end date even when other required fields are missing',()=>{const c=setup();c.auctionEnd='';c.submit();expect(c.fieldError('price')).toContain('cenę');expect(c.fieldError('auctionEnd')).toContain('termin');});
 it('rejects end dates outside the allowed window and clears the error after editing',()=>{const c=setup();c.auctionEnd=new Date(Date.now()+30*60000).toISOString();c.submit();expect(c.fieldError('auctionEnd')).toContain('1 godziny do 7 dni');c.auctionFieldChanged('auctionEnd',new Date(Date.now()+86400000).toISOString());expect(c.fieldError('auctionEnd')).toBe('');});
 it('maps backend auction names to visible form fields',()=>{const c=setup();c.applyServerErrors(new HttpErrorResponse({status:400,error:{errors:{startingAmount:'Błędna cena',endsAt:'Błędny termin',description:'Niedozwolone słowo'}}}));expect(c.fieldError('price')).toBe('Błędna cena');expect(c.fieldError('auctionEnd')).toBe('Błędny termin');expect(c.fieldError('auctionDescription')).toBe('Niedozwolone słowo');});
});
describe('suggested price in the listing form',()=>{
 afterEach(()=>TestBed.resetTestingModule());
 function setup(auction=false){TestBed.configureTestingModule({imports:[ListingFormPageComponent],providers:[provideRouter([]),{provide:DictionaryService,useValue:{getAll:()=>of({servers:[],currencies:[{id:1,name:'w grze'},{id:2,name:'pln'}],rarities:[],itemTypes:[]})}},{provide:ListingService,useValue:{}},{provide:AuctionService,useValue:{config:()=>of({enabled:true,plnEnabled:true})}},{provide:ItemService,useValue:{search:()=>of([])}}]});const c=TestBed.createComponent(ListingFormPageComponent).componentInstance as any;c.auctionMode=auction;return c;}
 it('fills whole złote and switches to PLN when no currency is chosen',()=>{const c=setup();c.useSuggestedPrice({grosze:18_480,gold:3_696_000_000});expect(c.form.controls.currencyId.value).toBe(2);expect(c.form.controls.price.value).toBe('185');});
 it('fills gold when gold is chosen',()=>{const c=setup();c.form.controls.currencyId.setValue(1);c.useSuggestedPrice({grosze:9_000,gold:1_234_567_890});expect(c.form.controls.price.value).toBe('1,2g');expect(c.form.controls.currencyId.value).toBe(1);c.useSuggestedPrice({grosze:9_000,gold:456_789_000});expect(c.form.controls.price.value).toBe('457m');});
 it('keeps gold above 2g in gold instead of switching to PLN',()=>{const c=setup();c.form.controls.currencyId.setValue(1);c.useSuggestedPrice({grosze:18_480,gold:6_480_000_000});expect(c.form.controls.currencyId.value).toBe(1);expect(c.form.controls.price.value).toBe('6,5g');});
 it('rewrites a valid gold price to one form after leaving the field',()=>{const c=setup();c.form.controls.currencyId.setValue(1);for(const text of ['6500m','6.5g','6 500m','6500000000']){c.form.controls.price.setValue(text);c.normalizePrice();expect(c.form.controls.price.value,text).toBe('6,5g');}c.form.controls.price.setValue('6,5k');c.normalizePrice();expect(c.form.controls.price.value).toBe('6,5k');});
 it('leaves PLN prices as typed',()=>{const c=setup();c.form.controls.currencyId.setValue(2);c.form.controls.price.setValue('6500');c.normalizePrice();expect(c.form.controls.price.value).toBe('6500');});
 it('asks once to confirm a gold price under 1m and lets the same amount through on the second try',()=>{const c=setup();expect(c.needsLowGoldConfirm(133)).toBe(true);expect(c.lowGoldConfirm).toBe(133);expect(c.needsLowGoldConfirm(133)).toBe(false);expect(c.lowGoldConfirm).toBeNull();expect(c.needsLowGoldConfirm(100_000_000)).toBe(false);});
 it('drops a pending low gold confirmation when the price changes',()=>{const c=setup();c.ngOnInit?.();c.needsLowGoldConfirm(133);c.form.controls.price.setValue('133m');expect(c.lowGoldConfirm).toBeNull();});
 it('keeps large gold amounts for auctions',()=>{const a=setup(true);a.form.controls.currencyId.setValue(1);a.useSuggestedPrice({grosze:18_480,gold:3_696_000_000});expect(a.form.controls.price.value).toBe('3,7g');});
 it('does not turn a small auction amount into plain units that would read as millions',()=>{const a=setup(true);a.form.controls.currencyId.setValue(1);a.form.controls.price.setValue('0,5m');a.normalizePrice();expect(a.form.controls.price.value).toBe('0,5m');a.form.controls.price.setValue('6500');a.normalizePrice();expect(a.form.controls.price.value).toBe('6,5g');});
});
