import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { UserModerationComponent } from './user-moderation.component';

describe('profile moderation controls',()=>{
  let http:HttpTestingController;
  const url='/api/admin/users/7/moderation';
  const empty={active:[],history:{content:[],page:1,size:20,totalElements:0,totalPages:0,first:true,last:true}};
  beforeEach(()=>{TestBed.configureTestingModule({imports:[UserModerationComponent],providers:[provideHttpClient(),provideHttpClientTesting(),provideRouter([])]});http=TestBed.inject(HttpTestingController);});
  afterEach(()=>{http.verify();TestBed.resetTestingModule();});
  function create(){const fixture=TestBed.createComponent(UserModerationComponent);fixture.componentRef.setInput('userId',7);fixture.componentRef.setInput('displayName','Gracz');fixture.detectChanges();http.expectOne(url+'?page=1').flush(empty);return fixture;}
  it('sends selected scopes with a duration',()=>{
    const fixture=create(),c=fixture.componentInstance as any;c.selected={ITEM_LISTINGS:true,BIDDING:true};c.reason='Uzasadnienie';c.duration=2;c.unit=3600;c.submit();
    const request=http.expectOne(url+'/restrictions');expect(request.request.body).toEqual({scopes:['ITEM_LISTINGS','BIDDING'],reason:'Uzasadnienie',durationSeconds:7200});
    request.flush(null);http.expectOne(url+'?page=1').flush(empty);fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('otrzymał powiadomienie');fixture.destroy();
  });
  it('uses a specific end date or permanent access block without mixing modes',()=>{
    const fixture=create(),c=fixture.componentInstance as any;c.selected={ACCOUNT:true};c.reason='Powód';c.timeMode='UNTIL';c.until=new Date(Date.now()+86400000).toISOString().slice(0,16);c.submit();
    const dated=http.expectOne(url+'/restrictions');expect(dated.request.body.expiresAt).toBe(new Date(c.until).toISOString());expect(dated.request.body.durationSeconds).toBeUndefined();dated.flush(null);http.expectOne(url+'?page=1').flush(empty);
    c.reason='Stała blokada';c.timeMode='PERMANENT';c.submit();const permanent=http.expectOne(url+'/restrictions');expect(permanent.request.body).toEqual({scopes:['ACCOUNT'],reason:'Stała blokada',permanent:true});permanent.flush(null);http.expectOne(url+'?page=1').flush(empty);fixture.destroy();
  });
  it('sends warnings independently of restriction settings',()=>{
    const fixture=create(),c=fixture.componentInstance as any;c.kind='WARNING';c.reason='Upomnienie';c.selected={};c.submit();const request=http.expectOne(url+'/warnings');expect(request.request.body).toEqual({reason:'Upomnienie'});request.flush(null);http.expectOne(url+'?page=1').flush(empty);fixture.destroy();
  });
  it('requires an explanation and selected scope before posting',()=>{
    const fixture=create(),c=fixture.componentInstance as any;c.selected={ACCOUNT:true};c.reason=' ';c.submit();c.reason='Powód';c.selected={};c.submit();http.expectNone(r=>r.method==='POST');fixture.destroy();
  });
});
