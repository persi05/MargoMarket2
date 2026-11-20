import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, OnInit } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../../core/models/api.models';
import { auctionAmount } from '../../core/utils/auction-format';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { ConversationReportDialogComponent } from '../messages/conversation-report-dialog.component';

interface RecordRow {
  id:number; auction_id?:number; user_id?:number; bid_id?:number; bidder_id?:number; item_name?:string;
  reporter_name?:string; bidder_name?:string; amount?:number; currency?:'PLN'|'GOLD'; bid_invalidated?:boolean;
  action?:string; reason:string; created_at:string; expires_at?:string; appeal_instructions?:string;
  revoked_at?:string; resolved_at?:string; resolution?:string;
}
@Component({selector:'mm-auction-moderation', standalone:true,
  imports:[FormsModule,RouterLink,DatePipe,PaginationComponent,ConversationReportDialogComponent],
  templateUrl:'./auction-moderation.component.html', styleUrl:'./admin-workspace.css'})
export class AuctionModerationComponent implements OnInit {
  private readonly http=inject(HttpClient);
  private readonly destroy=inject(DestroyRef);
  private readonly url=environment.apiUrl+'/admin/auctions';
  protected kind='reports'; protected state='OPEN'; protected reason=''; protected appeal=''; protected end=''; protected userId:number|null=null;
  protected result:PageResponse<RecordRow>|null=null;
  protected error='';protected notice='';protected loading=false;protected busy=false;protected dialogError='';
  protected selected:{row:RecordRow;action:'review'|'invalidate'|'revoke'}|null=null;
  protected readonly amount=auctionAmount;
  private generation=0;
  ngOnInit() {this.load();}
  protected selectKind(kind:string) {this.kind=kind;this.result=null;this.load();}
  protected load(page=1) {
    const generation=++this.generation;this.loading=true;this.error='';
    this.http.get<PageResponse<RecordRow>>(this.url+'/register/'+this.kind,{params:{page,state:this.state}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:r=>{if(generation!==this.generation)return;if(r.totalElements>0 && r.page>r.totalPages){this.load(r.totalPages);return;}this.result=r;this.loading=false;},
      error:e=>{if(generation!==this.generation)return;this.loading=false;this.failure(e);}
    });
  }
  protected open(row:RecordRow,action:'review'|'invalidate'|'revoke') {this.selected={row,action};this.dialogError='';}
  protected decide(value:{details:string}) {
    if(!this.selected || this.busy)return;
    const {row,action}=this.selected;this.busy=true;this.dialogError='';
    const request=action==='revoke' ? this.http.post(this.url+'/restrictions/'+row.id+'/revoke',{reason:value.details})
      : this.http.post(this.url+'/reports/'+row.id+'/review',{reason:value.details,invalidateBid:action==='invalidate'});
    request.pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.busy=false;this.selected=null;this.notice='Decyzja została zapisana.';this.load(this.result?.page ?? 1);},error:(e:HttpErrorResponse)=>{this.busy=false;this.dialogError=e.error?.message ?? 'Nie udało się zapisać decyzji.';}});
  }
  protected restrict() {
    if(this.busy || !this.userId || !this.reason.trim() || !this.appeal.trim())return;
    const end=new Date(this.end);if(!Number.isFinite(end.getTime())){this.error='Podaj termin ograniczenia.';return;}
    this.busy=true;this.error='';
    this.http.post(this.url+'/users/'+this.userId+'/restrictions',{reason:this.reason,appealInstructions:this.appeal,expiresAt:end.toISOString()}).pipe(takeUntilDestroyed(this.destroy)).subscribe({
      next:()=>{this.busy=false;this.reason='';this.appeal='';this.end='';this.userId=null;this.notice='Ograniczenie zostało zapisane.';this.load();},error:e=>{this.busy=false;this.failure(e);}
    });
  }
  private failure(e:HttpErrorResponse) {this.error=e.error?.message ?? 'Nie udało się pobrać lub zapisać danych.';}
}
