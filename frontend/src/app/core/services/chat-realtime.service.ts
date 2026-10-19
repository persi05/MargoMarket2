import { Injectable, NgZone, inject } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { Subject } from 'rxjs';

import { AuthService } from './auth.service';

export type ChatRealtimeEvent =
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
