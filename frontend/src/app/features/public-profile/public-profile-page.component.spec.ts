import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { provideRouter, ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { PublicProfilePageComponent } from './public-profile-page.component';
import { environment } from '../../../environments/environment';
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
});
