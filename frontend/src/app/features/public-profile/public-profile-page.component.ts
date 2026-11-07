import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { switchMap, forkJoin, catchError, of } from 'rxjs';
import { environment } from '../../../environments/environment';
import { ListingResponse, PageResponse } from '../../core/models/api.models';
import { ListingCardComponent } from '../../shared/listing-card/listing-card.component';
interface Profile { id:number; displayName:string; createdAt:string; activeListings:number; soldListings:number; confirmedPurchases:number; confirmedSales:number; }
@Component({selector:'mm-public-profile-page', standalone:true,imports:[DatePipe,RouterLink,ListingCardComponent],templateUrl:'./public-profile-page.component.html',styleUrl:'./public-profile-page.component.css'})
export class PublicProfilePageComponent {
 private http=inject(HttpClient); private route=inject(ActivatedRoute);
 protected profile:Profile|null=null; protected listings:PageResponse<ListingResponse>|null=null;
 protected loading=true; protected error='';
 constructor(){this.route.paramMap.pipe(switchMap(params=>{
 this.loading=true;this.error='';this.profile=null;this.listings=null;
 const id=params.get('id');
 return forkJoin({profile:this.http.get<Profile>(environment.apiUrl+'/profiles/'+id),listings:this.http.get<PageResponse<ListingResponse>>(environment.apiUrl+'/profiles/'+id+'/listings')}).pipe(catchError(e=>{this.error=e.status===404?'Profil niedostępny.':'Nie udało się pobrać profilu. Spróbuj ponownie.';return of(null)}));
 }),takeUntilDestroyed()).subscribe(result=>{this.loading=false;if(result){this.profile=result.profile;this.listings=result.listings}})}
 protected page(page:number){if(!this.profile)return;this.loading=true;this.http.get<PageResponse<ListingResponse>>(environment.apiUrl+'/profiles/'+this.profile.id+'/listings?page='+page).subscribe({next:r=>{this.listings=r;this.loading=false},error:()=>{this.error='Nie udało się pobrać ogłoszeń.';this.loading=false}})}
}
