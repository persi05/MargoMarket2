import { Injectable, NgZone, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import { Subject } from 'rxjs';

import { AuthService } from './auth.service';

export type ChatRealtimeEvent =
  | { kind: 'auction'; auctionId: number }
  | { kind: 'connected' }
  | { kind: 'disconnected' }
  | { kind: 'read' }
  | { kind: 'personal' | 'admin'; conversationId: number };

@Injectable({ providedIn: 'root' })
export class ChatRealtimeService {
  private readonly auth = inject(AuthService);
  private readonly zone = inject(NgZone);
  private readonly eventsSubject = new Subject<ChatRealtimeEvent>();
  private client: Client | null = null;
  private currentToken: string | null = null;
  private admin = false;
  private auctionId: number | null = null;
  private auctionSubscription: StompSubscription | null = null;

  watchAuction(id: number): void {
    this.stopAuction();
    this.auctionId = id;
    this.subscribeAuction();
  }
  stopAuction(): void {
    if (this.client?.connected) this.auctionSubscription?.unsubscribe();
    this.auctionSubscription = null;
    this.auctionId = null;
  }
  private subscribeAuction(): void {
    const id = this.auctionId;
    if (!this.client?.connected || !id) return;
    this.auctionSubscription = this.client.subscribe(`/topic/auctions/${id}`, () => {
      this.zone.run(() => this.eventsSubject.next({kind:'auction',auctionId:id}));
    });
  }

  readonly events$ = this.eventsSubject.asObservable();
  connected = false;

  connect(admin: boolean): void {
    const token = this.auth.token;
    if (!token) {
      this.disconnect();
      return;
    }
    if (this.client && this.currentToken === token && this.admin === admin) return;
    this.disconnect();
    this.currentToken = token;
    this.admin = admin;

    const protocol = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const client = new Client({
      webSocketFactory: () => new WebSocket(`${protocol}//${location.host}/ws/conversations`),
      connectHeaders: { Authorization: `Bearer ${token}` },
      reconnectDelay: 5000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => {
        if (this.client !== client) return;
        client.subscribe('/user/queue/conversation-events', message => this.receive(message, 'personal'));
        if (admin) client.subscribe('/topic/admin-conversations', message => this.receive(message, 'admin'));
        this.subscribeAuction();
        this.zone.run(() => {
          this.connected = true;
          this.eventsSubject.next({ kind: 'connected' });
        });
      },
      onWebSocketClose: () => {
        if (this.client !== client) return;
        this.zone.run(() => {
          this.connected = false;
          this.eventsSubject.next({ kind: 'disconnected' });
        });
      }
    });
    this.client = client;
    client.activate();
  }

  disconnect(): void {
    const client = this.client;
    this.client = null;
    this.currentToken = null;
    this.connected = false;
    if (client) void client.deactivate();
  }

  notifyRead(): void {
    this.eventsSubject.next({ kind: 'read' });
  }

  private receive(message: IMessage, kind: 'personal' | 'admin'): void {
    try {
      const payload = JSON.parse(message.body) as { conversationId?: number };
      const conversationId = payload.conversationId;
      if (typeof conversationId === 'number') {
        this.zone.run(() => this.eventsSubject.next({ kind, conversationId }));
      }
    } catch {
      // A later REST refresh restores the current state after an invalid event.
    }
  }
}
