import { TestBed } from '@angular/core/testing';
import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { AuthService } from '../services/auth.service';
import { AccountAccessService } from '../services/account-access.service';
import { authInterceptor } from './auth.interceptor';

describe('account access responses',()=>{
  const auth={token:'test-token',logout:vi.fn()};let http:HttpTestingController;
  beforeEach(()=>{auth.logout.mockClear();TestBed.configureTestingModule({providers:[provideHttpClient(withInterceptors([authInterceptor])),provideHttpClientTesting(),{provide:AuthService,useValue:auth}]});http=TestBed.inject(HttpTestingController);});
  afterEach(()=>{http.verify();TestBed.resetTestingModule();});
  function request(code:string,status=403){TestBed.inject(HttpClient).get('/api/items').subscribe({error:()=>{}});http.expectOne('/api/items').flush({code,reason:'Powód decyzji',expiresAt:null},{status,statusText:'Forbidden'});}
  it('keeps the blocked session so the reason can be shown',()=>{request('ACCOUNT_BLOCKED');expect(TestBed.inject(AccountAccessService).block()?.reason).toBe('Powód decyzji');expect(auth.logout).not.toHaveBeenCalled();});
  it('does not turn a limited action into a complete account block',()=>{request('ACTION_RESTRICTED');expect(TestBed.inject(AccountAccessService).block()).toBeNull();expect(auth.logout).not.toHaveBeenCalled();});
  it('still logs out an expired session',()=>{request('EXPIRED',401);expect(auth.logout).toHaveBeenCalledOnce();});
});
