import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../../core/models/api.models';
interface Confirmation { id:number; buyer_id:number|null; seller_id:number|null; confirmed_at:string; revoked_at:string|null; revoke_reason:string|null; }
@Component({selector:'mm-exchange-confirmations',standalone:true,imports:[FormsModule,DatePipe,RouterLink],template:`
<section><h2>Potwierdzenia wymian</h2><p>Unieważnienie usuwa potwierdzenie ze statystyk obu stron i pozostawia ślad audytowy.</p>
@if(error){<p role="alert">{{error}}</p>}
@for(c of result?.content || [];track c.id){<article><strong>Wymiana #{{c.id}}</strong> · {{c.confirmed_at | date:'dd.MM.yyyy HH:mm'}}
<p>@if(c.buyer_id){<a [routerLink]="['/u',c.buyer_id,'profil']">Kupujący #{{c.buyer_id}}</a>} · @if(c.seller_id){<a [routerLink]="['/u',c.seller_id,'profil']">Sprzedawca #{{c.seller_id}}</a>}</p>
@if(c.revoked_at){<p>Unieważnione: {{c.revoke_reason}}</p>}@else{<form (ngSubmit)="revoke(c.id)"><label [for]="'revoke-'+c.id">Uzasadnienie unieważnienia</label><input [id]="'revoke-'+c.id" [name]="'reason-'+c.id" [(ngModel)]="reasons[c.id]" required maxlength="1000"><button [disabled]="busy || !reasons[c.id]?.trim()">Unieważnij potwierdzenie</button></form>}</article>}
@if(result;as r){<button [disabled]="busy || r.first" (click)="load(r.page-1)">Poprzednia</button><span> Strona {{r.page}} </span><button [disabled]="busy || r.last" (click)="load(r.page+1)">Następna</button>}
</section>`,styles:[`section{margin-top:24px;padding:20px;border:1px solid var(--color-border)}article{padding:16px 0;border-bottom:1px solid var(--color-border)}a{color:var(--color-primary)}form{display:flex;flex-wrap:wrap;gap:12px;align-items:center}input{min-width:200px;flex:1}p{color:var(--color-text-muted)}`]})
export class ExchangeConfirmationsComponent {
 private http=inject(HttpClient);protected result:PageResponse<Confirmation>|null=null;protected reasons:Record<number,string>={};protected error='';protected busy=false;
 constructor(){this.load()}
 protected load(page=1){this.busy=true;this.error='';this.http.get<PageResponse<Confirmation>>(environment.apiUrl+'/admin/exchange-confirmations?page='+page).subscribe({next:r=>{this.result=r;this.busy=false},error:()=>{this.error='Nie udało się pobrać potwierdzeń.';this.busy=false}})}
 protected revoke(id:number){const reason=this.reasons[id]?.trim();if(!reason || this.busy)return;this.busy=true;this.http.post(environment.apiUrl+'/admin/exchange-confirmations/'+id+'/revoke',{reason}).subscribe({next:()=>this.load(this.result?.page||1),error:()=>{this.error='Nie udało się unieważnić potwierdzenia.';this.busy=false}})}
}
