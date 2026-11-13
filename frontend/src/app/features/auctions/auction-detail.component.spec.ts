import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, convertToParamMap, provideRouter } from '@angular/router';
import { BehaviorSubject, Subject } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { environment } from '../../../environments/environment';
import { AuthService } from '../../core/services/auth.service';
import { ChatRealtimeService } from '../../core/services/chat-realtime.service';
import { AuctionDetailComponent } from './auction-detail.component';

describe('auction bidding', () => {
  let http: HttpTestingController;
  let user$: BehaviorSubject<unknown>;
  let auth: {isLoggedIn:boolean;user$:BehaviorSubject<unknown>};
  let socket: {connected:boolean; events$:Subject<any>; connect:ReturnType<typeof vi.fn>; watchAuction:ReturnType<typeof vi.fn>; stopAuction:ReturnType<typeof vi.fn>};
  const url=`${environment.apiUrl}/auctions/7`;
  function state(owner=false) {
    return {id:7,listing:{id:7,itemName:'Miecz',server:{id:1,name:'Syberia'},sellerId:2,sellerDisplayName:'Sprzedawca'},currency:'PLN',
      startingAmount:1000,highestAmount:2000,nextMinimumBid:2100,endsAt:new Date(Date.now()+3600000).toISOString(),
      originalEndsAt:new Date(Date.now()+3600000).toISOString(),serverTime:new Date().toISOString(),status:'ACTIVE',version:1,bidCount:1,owner,leading:false,winner:false};
  }
  function flushState(value=state()) {
    http.expectOne(url).flush(value);
    http.expectOne(`${url}/bids?page=1`).flush({content:[],page:1,first:true,last:true});
  }
  beforeEach(() => {
    user$=new BehaviorSubject<unknown>({role:'user'});
    auth={isLoggedIn:true,user$};
    socket={connected:false,events$:new Subject(),connect:vi.fn(),watchAuction:vi.fn(),stopAuction:vi.fn()};
    TestBed.configureTestingModule({imports:[AuctionDetailComponent],providers:[provideHttpClient(),provideHttpClientTesting(),provideRouter([]),
      {provide:ActivatedRoute,useValue:{snapshot:{paramMap:convertToParamMap({id:'7'})}}},
      {provide:AuthService,useValue:auth},
      {provide:ChatRealtimeService,useValue:socket}
    ]});
    http=TestBed.inject(HttpTestingController);
  });
  afterEach(() => {http.verify();TestBed.resetTestingModule();vi.useRealTimers();});
  it('sends exact minor units, prevents double submission and reuses the key after network failure', () => {
    const fixture=TestBed.createComponent(AuctionDetailComponent);
    const c=fixture.componentInstance as any;
    flushState(); http.expectOne(`${environment.apiUrl}/auctions/config`).flush({enabled:true,plnEnabled:true});
    c.input='25,50';c.bid();c.bid();
    const first=http.expectOne(`${url}/bids`);
    expect(first.request.body.amount).toBe(2550);
    const key=first.request.body.idempotencyKey;
    first.error(new ProgressEvent('error'));flushState();
    c.bid();const retry=http.expectOne(`${url}/bids`);
    expect(retry.request.body.idempotencyKey).toBe(key);
    retry.flush({id:9});flushState();
    expect(c.input).toBe('');expect(c.notice).toContain('przyjęta');
  });
  it('hides the bid form for an owner', () => {
    const fixture=TestBed.createComponent(AuctionDetailComponent);
    flushState(state(true));http.expectOne(`${environment.apiUrl}/auctions/config`).flush({enabled:true,plnEnabled:true});
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('input[name="bid"]')).toBeNull();
  });
  it('clears private state immediately on logout and ignores the previous response', () => {
    const fixture=TestBed.createComponent(AuctionDetailComponent);
    const old=http.expectOne(url);const oldHistory=http.expectOne(`${url}/bids?page=1`);
    http.expectOne(`${environment.apiUrl}/auctions/config`).flush({enabled:true,plnEnabled:true});
    auth.isLoggedIn=false;user$.next(null);
    expect((fixture.componentInstance as any).auction).toBeNull();
    old.flush(state());oldHistory.flush({content:[],page:1});
    expect((fixture.componentInstance as any).auction).toBeNull();
    const publicState={...state(),startingAmount:null,highestAmount:null,nextMinimumBid:null};
    http.expectOne(url).flush(publicState);http.expectOne(`${url}/bids?page=1`).flush({content:[],page:1});
    fixture.detectChanges();expect(fixture.nativeElement.textContent).toContain('Zaloguj się');
    expect(fixture.nativeElement.querySelector('input[name="bid"]')).toBeNull();
  });
  it('uses live events instead of polling while connected and coalesces a burst of updates', () => {
    vi.useFakeTimers();
    const fixture=TestBed.createComponent(AuctionDetailComponent);
    flushState();http.expectOne(`${environment.apiUrl}/auctions/config`).flush({enabled:true,plnEnabled:true});
    socket.connected=true;
    vi.advanceTimersByTime(10000);
    http.expectNone(() => true);
    socket.events$.next({kind:'auction',auctionId:7});
    socket.events$.next({kind:'auction',auctionId:7});
    vi.advanceTimersByTime(500);
    flushState();
    http.expectNone(() => true);
    fixture.destroy();
  });
  it('falls back to polling when the live connection is unavailable', () => {
    vi.useFakeTimers();
    const fixture=TestBed.createComponent(AuctionDetailComponent);
    flushState();http.expectOne(`${environment.apiUrl}/auctions/config`).flush({enabled:true,plnEnabled:true});
    vi.advanceTimersByTime(10000);
    flushState();
    fixture.destroy();
  });
});
