import { Injectable, inject, signal } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { AuthService } from './auth.service';
import { PlatformApi } from './sales.api';
import { AppNotification } from './sales.models';
import { ToastService } from './toast.service';

/**
 * Notifications temps réel : connexion STOMP sur WebSocket (/ws), abonnement à la file
 * personnelle de l'utilisateur. Reconnexion automatique si le serveur redémarre.
 */
@Injectable({ providedIn: 'root' })
export class RealtimeService {
  private auth = inject(AuthService);
  private api = inject(PlatformApi);
  private toast = inject(ToastService);
  private client: Client | null = null;

  readonly unread = signal(0);
  readonly latest = signal<AppNotification[]>([]);
  readonly connected = signal(false);

  start(): void {
    if (this.client) return;
    this.refresh();
    const protocol = location.protocol === 'https:' ? 'wss' : 'ws';
    this.client = new Client({
      brokerURL: `${protocol}://${location.host}/ws`,
      reconnectDelay: 5000,
      // Le jeton est relu à chaque (re)connexion.
      beforeConnect: () => {
        this.client!.connectHeaders = { Authorization: `Bearer ${this.auth.token ?? ''}` };
      },
      onConnect: () => {
        this.connected.set(true);
        this.client!.subscribe('/user/queue/notifications', (msg: IMessage) => this.onMessage(msg));
      },
      onWebSocketClose: () => this.connected.set(false),
      onStompError: () => this.connected.set(false)
    });
    this.client.activate();
  }

  stop(): void {
    this.client?.deactivate();
    this.client = null;
    this.connected.set(false);
    this.unread.set(0);
    this.latest.set([]);
  }

  refresh(): void {
    this.api.unread().subscribe({ next: (r) => this.unread.set(r.count), error: () => {} });
    this.api.notifications(0).subscribe({ next: (p) => this.latest.set(p.content.slice(0, 10)), error: () => {} });
  }

  markAllRead(): void {
    this.api.markAllRead().subscribe(() => {
      this.unread.set(0);
      this.latest.update((list) => list.map((n) => ({ ...n, readAt: n.readAt ?? new Date().toISOString() })));
    });
  }

  markRead(n: AppNotification): void {
    if (n.readAt) return;
    this.api.markRead(n.id).subscribe(() => {
      this.unread.update((c) => Math.max(0, c - 1));
      this.latest.update((list) => list.map((x) => (x.id === n.id ? { ...x, readAt: new Date().toISOString() } : x)));
    });
  }

  private onMessage(msg: IMessage): void {
    const n = JSON.parse(msg.body) as AppNotification;
    this.unread.update((c) => c + 1);
    this.latest.update((list) => [n, ...list].slice(0, 10));
    this.toast.info(n.title);
  }
}
