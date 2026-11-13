import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { environment } from '../../../environments/environment';
import { ListingResponse, PageResponse } from '../models/api.models';

export interface Auction {
  id: number;
  listing: ListingResponse;
  currency: 'PLN' | 'GOLD';
  startingAmount: number | null;
  highestAmount: number | null;
  nextMinimumBid: number | null;
  originalEndsAt: string;
  endsAt: string;
  serverTime: string;
  status: string;
  version: number;
  bidCount: number;
  leading: boolean;
  owner: boolean;
  winner: boolean;
  fulfillmentStatus: string | null;
  conversationId: number | null;
  contactReminder: boolean;
  winnerId: number | null;
  winnerDisplayName: string | null;
}
export interface AuctionBid {
  id: number;
  bidder: string;
  own: boolean;
  amount: number | null;
  currency: 'PLN' | 'GOLD';
  acceptedAt: string;
  invalidated: boolean;
  userId?: number;
}
export interface AuctionRequest {
  itemId: number;
  serverId: number;
  enhancementLevel: number;
  bound: boolean;
  currency: 'PLN' | 'GOLD';
  startingAmount: number;
  endsAt: string;
  description: string;
  contact: string;
}
@Injectable({ providedIn: 'root' })
export class AuctionService {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiUrl}/auctions`;
  config() { return this.http.get<{enabled: boolean; plnEnabled: boolean; attemptsPerMinute:number; attemptSpacingSeconds:number}>(`${this.url}/config`); }
  list(filters: Record<string, string | number>) {
    let params = new HttpParams();
    for (const [key, value] of Object.entries(filters)) if (value !== '') params = params.set(key, value);
    return this.http.get<PageResponse<Auction>>(this.url, {params});
  }
  get(id: number) { return this.http.get<Auction>(`${this.url}/${id}`); }
  create(r: AuctionRequest) { return this.http.post<Auction>(this.url, r); }
  update(id: number, r: AuctionRequest) { return this.http.put<Auction>(`${this.url}/${id}`, r); }
  bids(id: number, page: number) { return this.http.get<PageResponse<AuctionBid>>(`${this.url}/${id}/bids`, {params: {page}}); }
  bid(id: number, amount: number, currency: string, idempotencyKey: string) {
    return this.http.post(`${this.url}/${id}/bids`, {amount, currency, idempotencyKey});
  }
  report(id: number, reason: string) { return this.http.post(`${this.url}/${id}/reports`, {reason}); }
  cancel(id: number, reason: string) { return this.http.post(`${this.url}/${id}/cancel`, {reason}); }
  start(id: number) { return this.http.post<{conversationId: number}>(`${this.url}/${id}/fulfillment/start`, {}); }
  action(id: number, action: string, reason: string) { return this.http.post(`${this.url}/${id}/fulfillment/actions`, {action, reason}); }
  invalidate(id: number, bidId: number, reason: string) {
    return this.http.post(`${environment.apiUrl}/admin/auctions/${id}/bids/${bidId}/invalidate`, {reason});
  }
}
