import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject, Input, OnChanges, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../../core/models/api.models';
import { PaginationComponent } from '../../shared/pagination/pagination.component';
import { ConversationReportDialogComponent } from '../messages/conversation-report-dialog.component';

export interface ModerationDecision {id:number;kind:'WARNING'|'RESTRICTION';scope:string|null;reason:string;createdAt:string;expiresAt:string|null;revokedAt:string|null;revokeReason:string|null;actorName:string|null;}
interface Overview {active:ModerationDecision[];history:PageResponse<ModerationDecision>;}
@Component({selector:'mm-user-moderation',standalone:true,imports:[FormsModule,DatePipe,PaginationComponent,ConversationReportDialogComponent],templateUrl:'./user-moderation.component.html',styleUrl:'./admin-workspace.css'})
export class UserModerationComponent implements OnChanges {
  @Input({required:true}) userId!:number;@Input({required:true}) displayName='';
  private readonly http=inject(HttpClient);private readonly destroy=inject(DestroyRef);private generation=0;
  protected readonly data=signal<Overview|null>(null);protected readonly busy=signal(false);protected readonly loading=signal(false);protected readonly error=signal('');protected readonly notice=signal('');
  protected readonly scopes=[{id:'ACCOUNT',label:'Całe konto',help:'Blokuje dostęp do wszystkich funkcji konta.'},{id:'ITEM_LISTINGS',label:'Wystawianie przedmiotów',help:'Publikowanie i edycja zwykłych ogłoszeń.'},{id:'GOLD_LISTINGS',label:'Wystawianie złota',help:'Publikowanie i edycja ofert złota.'},{id:'AUCTION_LISTINGS',label:'Wystawianie licytacji',help:'Tworzenie i edycja licytacji.'},{id:'BIDDING',label:'Licytowanie',help:'Składanie ofert w licytacjach.'},{id:'MESSAGES',label:'Wysyłanie wiadomości',help:'Nowe rozmowy i wysyłanie wiadomości. Historia pozostaje dostępna.'},{id:'COMMENTS',label:'Komentowanie przedmiotów',help:'Dodawanie wpisów w dyskusjach.'}];
  protected kind:'WARNING'|'RESTRICTION'='RESTRICTION';protected selected:Record<string,boolean>={};protected reason='';protected timeMode:'DURATION'|'UNTIL'|'PERMANENT'='DURATION';protected duration=1;protected unit=86400;protected until='';protected revokeDecision:ModerationDecision|null=null;protected readonly revokeError=signal('');
  private get url(){return environment.apiUrl+'/admin/users/'+this.userId+'/moderation';}
  ngOnChanges(){this.reason='';this.selected={};this.revokeDecision=null;this.data.set(null);this.load();}
  protected label(scope:string|null){return this.scopes.find(s=>s.id===scope)?.label ?? 'Upomnienie';}
  protected load(page=1){const generation=++this.generation;this.loading.set(true);this.error.set('');this.http.get<Overview>(this.url,{params:{page}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:data=>{if(generation!==this.generation)return;this.data.set(data);this.loading.set(false);},error:e=>{if(generation!==this.generation)return;this.loading.set(false);this.error.set(this.message(e));}});}
  protected get canSubmit(){return !!this.reason.trim() && (this.kind==='WARNING' || Object.values(this.selected).some(Boolean));}
  protected submit(){
    if(this.busy() || !this.canSubmit)return;
    const body:Record<string,unknown>={reason:this.reason.trim()};
    if(this.kind==='RESTRICTION'){
      body['scopes']=Object.keys(this.selected).filter(key=>this.selected[key]);
      if(this.timeMode==='PERMANENT')body['permanent']=true;
      else if(this.timeMode==='UNTIL'){const date=new Date(this.until);if(!this.until || !Number.isFinite(date.getTime()) || date.getTime()<=Date.now()){this.error.set('Podaj datę zakończenia w przyszłości.');return;}body['expiresAt']=date.toISOString();}
      else {const seconds=this.duration*Number(this.unit);if(!Number.isSafeInteger(seconds) || seconds<1 || seconds>315360000){this.error.set('Podaj poprawny okres, maksymalnie 10 lat.');return;}body['durationSeconds']=seconds;}
    }
    const generation=this.generation;this.busy.set(true);this.error.set('');
    this.http.post(this.url+(this.kind==='WARNING'?'/warnings':'/restrictions'),body).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.busy.set(false);if(generation!==this.generation)return;this.reason='';this.notice.set('Decyzja została zapisana. Użytkownik otrzymał powiadomienie.');this.load();},error:e=>{this.busy.set(false);if(generation===this.generation)this.error.set(this.message(e));}});
  }
  protected revoke(value:{details:string}){if(!this.revokeDecision || this.busy())return;this.busy.set(true);this.revokeError.set('');const generation=this.generation;this.http.post(this.url+'/restrictions/'+this.revokeDecision.id+'/revoke',{reason:value.details}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.busy.set(false);if(generation!==this.generation)return;this.revokeDecision=null;this.notice.set('Blokada została cofnięta. Użytkownik otrzymał powiadomienie.');this.load();},error:e=>{this.busy.set(false);if(generation===this.generation)this.revokeError.set(this.message(e));}});}
  protected openRevoke(decision:ModerationDecision){this.revokeDecision=decision;this.revokeError.set('');}
  private message(e:HttpErrorResponse){return e.error?.message ?? 'Nie udało się pobrać lub zapisać decyzji.';}
}
