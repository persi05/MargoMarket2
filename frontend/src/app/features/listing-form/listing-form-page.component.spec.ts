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
