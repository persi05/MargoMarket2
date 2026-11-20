import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { Component, inject } from '@angular/core';
import { DatePipe } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../../core/models/api.models';
interface Confirmation { id:number; buyer_id:number|null; seller_id:number|null; confirmed_at:string; revoked_at:string|null; revoke_reason:string|null; }
@Component({selector:'mm-exchange-confirmations',standalone:true,imports:[FormsModule,DatePipe,RouterLink,PaginationComponent],templateUrl:'./exchange-confirmations.component.html',styleUrl:'./admin-workspace.css'})
export class ExchangeConfirmationsComponent {
 private http=inject(HttpClient);protected result:PageResponse<Confirmation>|null=null;protected reasons:Record<number,string>={};protected error='';protected busy=false;
 constructor(){this.load()}
 protected load(page=1){this.busy=true;this.error='';this.http.get<PageResponse<Confirmation>>(environment.apiUrl+'/admin/exchange-confirmations?page='+page).subscribe({next:r=>{this.result=r;this.busy=false},error:()=>{this.error='Nie udało się pobrać potwierdzeń.';this.busy=false}})}
 protected revoke(id:number){const reason=this.reasons[id]?.trim();if(!reason || this.busy)return;this.busy=true;this.http.post(environment.apiUrl+'/admin/exchange-confirmations/'+id+'/revoke',{reason}).subscribe({next:()=>this.load(this.result?.page||1),error:()=>{this.error='Nie udało się unieważnić potwierdzenia.';this.busy=false}})}
}
