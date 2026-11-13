import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { PublicProfilePageComponent } from './public-profile-page.component';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
describe('Public profile',()=>{
 beforeEach(()=>TestBed.configureTestingModule({imports:[PublicProfilePageComponent],providers:[provideHttpClient(),provideHttpClientTesting(),provideRouter([]),{provide:ActivatedRoute,useValue:{paramMap:of(convertToParamMap({id:'7',slug:'profil'}))}}]}));
 afterEach(()=>TestBed.inject(HttpTestingController).verify());
 it('separates confirmed exchanges from sold statuses',()=>{
 const fixture=TestBed.createComponent(PublicProfilePageComponent);const http=TestBed.inject(HttpTestingController);
 http.expectOne(environment.apiUrl+'/profiles/7').flush({id:7,displayName:'Rycerz',createdAt:'2026-01-01',activeListings:0,soldListings:30,confirmedPurchases:1,confirmedSales:2});
 http.expectOne(environment.apiUrl+'/profiles/7/listings').flush({content:[],page:1,first:true,last:true});fixture.detectChanges();
 expect(fixture.nativeElement.querySelector('.confirmed').textContent).toContain('administratora: 3');expect(fixture.nativeElement.textContent).toContain('30');expect(fixture.nativeElement.textContent).toContain('nie oznacza potwierdzenia');
 });
 it('shows an unavailable profile instead of private data',()=>{
 const fixture=TestBed.createComponent(PublicProfilePageComponent);const http=TestBed.inject(HttpTestingController);const profile=http.expectOne(environment.apiUrl+'/profiles/7');http.expectOne(environment.apiUrl+'/profiles/7/listings');profile.flush({}, {status:404,statusText:'Not Found'});fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Profil niedostępny');expect(fixture.nativeElement.querySelector('h1')).toBeNull();
 });

 it('shows a public seller link and blurs only the price for an anonymous visitor', () => {
   localStorage.clear();
   const fixture = TestBed.createComponent(PublicProfilePageComponent);
   const http = TestBed.inject(HttpTestingController);
   http.expectOne(environment.apiUrl + '/profiles/7').flush({id:7,displayName:'Rycerz',createdAt:'2026-01-01',activeListings:1,soldListings:0,confirmedPurchases:0,confirmedSales:0});
   http.expectOne(environment.apiUrl + '/profiles/7/listings').flush({
     content:[{id:1,itemName:'Miecz testowy',itemType:{id:1,name:'Jednoręczne'},rarity:{id:1,name:'Heroiczny'},level:100,
       currency:{id:1,name:'PLN'},server:{id:1,name:'Syberia'},price:null,contact:null,status:'active',listingKind:'ITEM',
       enhancementLevel:0,bound:false,sellerId:7,sellerDisplayName:'Rycerz',createdAt:'2026-01-01'}],
     page:1,totalPages:1,first:true,last:true
   });
   fixture.detectChanges();
   const card = fixture.nativeElement.querySelector('mm-listing-card');
   expect(card.querySelector('dl .seller-link').textContent.trim()).toBe('Rycerz');
   expect(card.querySelector('dl .seller-link').getAttribute('href')).toBe('/u/7/profil');
   expect(card.querySelector('dl mm-private-detail')).toBeNull();
   expect(card.querySelector('.price .masked')).not.toBeNull();
   expect(card.querySelector('dl .seller-link span')).toBeNull();
   const message = card.querySelector('footer .message-seller');
   expect(message.textContent.trim()).toBe('Napisz wiadomość');
   expect(message.getAttribute('href')).toBe('/messages/new/1');
   expect(card.querySelector('button.danger')).toBeNull();
   expect(fixture.nativeElement.querySelector('.offers > .message-seller')).toBeNull();
 });

 it('uses the shared pagination without reloading profile statistics', () => {
   const fixture = TestBed.createComponent(PublicProfilePageComponent);
   const http = TestBed.inject(HttpTestingController);
   http.expectOne(environment.apiUrl + '/profiles/7').flush({id:7,displayName:'Rycerz',createdAt:'2026-01-01',activeListings:21,soldListings:0,confirmedPurchases:0,confirmedSales:0});
   http.expectOne(environment.apiUrl + '/profiles/7/listings').flush({content:[],page:1,totalPages:2,first:true,last:false});
   fixture.detectChanges();
   const buttons = fixture.nativeElement.querySelectorAll('mm-pagination button');
   expect(buttons[0].disabled).toBe(true);
   buttons[1].click();
   http.expectOne(environment.apiUrl + '/profiles/7/listings?page=2').flush({content:[],page:2,totalPages:2,first:false,last:true});
   http.expectNone(environment.apiUrl + '/profiles/7');
   fixture.detectChanges();
   expect(fixture.nativeElement.querySelector('mm-pagination').textContent).toContain('Strona 2 z 2');
 });

 it('does not offer messaging for the viewers own item on a public profile', () => {
   TestBed.overrideProvider(AuthService, {useValue: {user$: of({id:7}), isLoggedIn:true}});
   const fixture = TestBed.createComponent(PublicProfilePageComponent);
   const http = TestBed.inject(HttpTestingController);
   http.expectOne(environment.apiUrl + '/profiles/7').flush({id:7,displayName:'Rycerz',createdAt:'2026-01-01',activeListings:1,soldListings:0,confirmedPurchases:0,confirmedSales:0});
   http.expectOne(environment.apiUrl + '/profiles/7/listings').flush({
     content:[{id:1,itemName:'Miecz testowy',itemType:{id:1,name:'Jednoręczne'},rarity:{id:1,name:'Heroiczny'},level:100,
       currency:{id:1,name:'PLN'},server:{id:1,name:'Syberia'},price:1500,contact:'',status:'active',listingKind:'ITEM',
       enhancementLevel:0,bound:false,sellerId:7,sellerDisplayName:'Rycerz',createdAt:'2026-01-01'}],
     page:1,totalPages:1,first:true,last:true
   });
   fixture.detectChanges();
   expect(fixture.nativeElement.querySelector('mm-listing-card .profile-listing')).not.toBeNull();
   expect(fixture.nativeElement.querySelector('mm-listing-card .message-seller')).toBeNull();
 });
});
