import { DatePipe } from '@angular/common';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { environment } from '../../../environments/environment';
import { PageResponse } from '../../core/models/api.models';

interface RecordRow { id:number; auction_id?:number; user_id?:number; action?:string; reason:string; created_at:string; expires_at?:string; appeal_instructions?:string; revoked_at?:string; resolved_at?:string; }
@Component({
  selector:'mm-auction-moderation', standalone:true, imports:[FormsModule,RouterLink,DatePipe],
  template: `
    <details (toggle)="opened($event)"><summary>Moderacja licytacji</summary>
      <p><a routerLink="/auctions" [queryParams]="{scope:'ADMIN'}">Otwórz licytacje</a>. Oferty unieważnia się w szczegółach aukcji. Zmiany wymagają uzasadnienia i pozostają w audycie.</p>
      @if (error) {<p role="alert">{{error}}</p>}
      <label>Rejestr<select [(ngModel)]="kind" (change)="load()"><option value="reports">Zgłoszenia</option><option value="restrictions">Ograniczenia udziału</option><option value="audit">Audyt aukcji</option></select></label>
      <label>Uzasadnienie decyzji<textarea [(ngModel)]="reason" maxlength="1000" rows="2"></textarea></label>
      @for (row of result?.content; track row.id) {
        <article><strong>Wpis {{row.id}}</strong><span>{{row.created_at | date:'dd.MM.yyyy HH:mm'}}</span>
          @if (row.auction_id) {<a [routerLink]="['/auctions',row.auction_id]">Aukcja {{row.auction_id}}</a>}
          @if (row.user_id) {<a [routerLink]="['/u',row.user_id,'profil']">Konto {{row.user_id}}</a>}
          @if (row.action) {<p>{{row.action}}</p>}<p>{{row.reason}}</p>
          @if (row.expires_at) {<p>Do {{row.expires_at | date:'dd.MM.yyyy HH:mm'}}. Odwołanie: {{row.appeal_instructions}}</p>}
          @if (kind === 'reports' && !row.resolved_at) {<button [disabled]="busy || !reason.trim()" (click)="decide(row.id,'resolve')">Rozpatrz zgłoszenie</button>}
          @if (kind === 'restrictions' && !row.revoked_at) {<button [disabled]="busy || !reason.trim()" (click)="decide(row.id,'revoke')">Cofnij ograniczenie</button>}
        </article>
      } @empty {<p>Brak wpisów w wybranym rejestrze.</p>}
      @if (result; as r) {<nav><button [disabled]="busy || r.first" (click)="load(r.page-1)">Poprzednia</button><span>Strona {{r.page}}</span><button [disabled]="busy || r.last" (click)="load(r.page+1)">Następna</button></nav>}
      <h3>Ogranicz udział konta</h3><p>Podaj udokumentowany powód, termin oraz sposób odwołania.</p>
      <form (ngSubmit)="restrict()"><label>ID konta<input name="userId" [(ngModel)]="userId" type="number" min="1" required></label><label>Koniec ograniczenia<input name="end" type="datetime-local" [(ngModel)]="end" required></label><label>Ścieżka odwołania<textarea name="appeal" [(ngModel)]="appeal" maxlength="1000" required></textarea></label><button [disabled]="busy || !reason.trim() || !appeal.trim() || !end || !userId">Zapisz ograniczenie</button></form>
    </details>`,
  styles:[`details{margin-top:28px;padding:20px;border:1px solid var(--color-border)}summary{cursor:pointer;font-weight:700}label{display:grid;gap:8px;margin:16px 0}article{border-bottom:1px solid var(--color-border);padding:16px 0}article>span,article>a{margin-left:16px}a{color:var(--color-primary)}nav{display:flex;gap:16px;margin:20px 0}textarea,input,select{max-width:600px;background:var(--color-input-dark);color:var(--color-text-main);padding:8px;border:1px solid var(--color-border);font:inherit}`]
})
export class AuctionModerationComponent {
  private readonly http=inject(HttpClient);
  private readonly destroy=inject(DestroyRef);
  private readonly url=`${environment.apiUrl}/admin/auctions`;
  protected kind='reports'; protected reason=''; protected appeal=''; protected end=''; protected userId:number|null=null;
  protected result:PageResponse<RecordRow>|null=null;
  protected error=''; protected busy=false;
  protected opened(event:Event) { if((event.target as HTMLDetailsElement).open && !this.result) this.load(); }
  protected load(page=1) {
    this.http.get<PageResponse<RecordRow>>(`${this.url}/register/${this.kind}`,{params:{page}}).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:r=>this.result=r,error:e=>this.failure(e)});
  }
  protected decide(id:number,action:string) { this.save(`${this.url}/${this.kind}/${id}/${action}`,{reason:this.reason}); }
  protected restrict() {
    const end=new Date(this.end); if(!Number.isFinite(end.getTime())) {this.error='Podaj termin ograniczenia.';return;}
    this.save(`${this.url}/users/${this.userId}/restrictions`,{reason:this.reason,appealInstructions:this.appeal,expiresAt:end.toISOString()});
  }
  private save(url:string,body:unknown) {
    if(this.busy)return;this.busy=true;this.error='';
    this.http.post(url,body).pipe(takeUntilDestroyed(this.destroy)).subscribe({next:()=>{this.busy=false;this.reason='';this.load();},error:e=>{this.busy=false;this.failure(e);}});
  }
  private failure(e:HttpErrorResponse) {this.error=e.error?.message || 'Nie udało się pobrać lub zapisać rejestru.';}
}
