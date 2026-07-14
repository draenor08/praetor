import { Injectable, inject } from '@angular/core';
import { Client, IMessage, StompSubscription } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import { Observable } from 'rxjs';
import { TokenService } from './token.service';

/** Live submission status frame pushed on /user/queue/submission/{id}. */
export interface SubmissionStatusEvent {
  id: number;
  status: string;
  verdict: string | null;
}

/**
 * STOMP-over-SockJS client for live updates. One shared connection for the app; each caller gets an
 * Observable for a destination that (re)subscribes automatically once the socket connects — and again
 * after any reconnect — and unsubscribes when the caller unsubscribes. Backend endpoint: SockJS /ws,
 * JWT carried on the CONNECT frame (Authorization header, read by StompAuthChannelInterceptor).
 */
@Injectable({ providedIn: 'root' })
export class WsService {
  private tokenService = inject(TokenService);
  private client?: Client;
  // Re-applied on every (re)connect so subscriptions survive a dropped socket.
  private readonly resubscribers = new Set<() => void>();

  private ensureClient(): Client {
    if (this.client) {
      return this.client;
    }
    const token = this.tokenService.getToken();
    this.client = new Client({
      webSocketFactory: () => new SockJS('/ws') as any,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      reconnectDelay: 3000,
      heartbeatIncoming: 10000,
      heartbeatOutgoing: 10000,
      onConnect: () => this.resubscribers.forEach((fn) => fn()),
    });
    this.client.activate();
    return this.client;
  }

  /** Live status for one submission: /user/queue/submission/{id}. */
  submission$(id: number): Observable<SubmissionStatusEvent> {
    return this.watch<SubmissionStatusEvent>(`/user/queue/submission/${id}`);
  }

  /** Live standings for a contest: /topic/contest/{id}/standings (frozen board during a freeze). */
  standings$(contestId: number): Observable<any> {
    return this.watch<any>(`/topic/contest/${contestId}/standings`);
  }

  /**
   * Privileged live standings: /user/queue/contest/{id}/standings. The backend pushes the unfrozen
   * board here to ADMIN/PROBLEM_SETTER subscribers during a freeze window (and replays a snapshot on
   * subscribe). Non-privileged subscribers receive nothing on this destination.
   */
  liveStandings$(contestId: number): Observable<any> {
    return this.watch<any>(`/user/queue/contest/${contestId}/standings`);
  }

  /** Subscribe to a destination; auto-(re)subscribes on (re)connect and parses JSON bodies. */
  private watch<T>(destination: string): Observable<T> {
    const client = this.ensureClient();
    return new Observable<T>((observer) => {
      let sub: StompSubscription | undefined;
      const subscribe = () => {
        sub = client.subscribe(destination, (m: IMessage) => {
          try {
            observer.next(JSON.parse(m.body) as T);
          } catch {
            observer.next(m.body as unknown as T);
          }
        });
      };
      this.resubscribers.add(subscribe);
      if (client.connected) {
        subscribe();
      }
      return () => {
        this.resubscribers.delete(subscribe);
        sub?.unsubscribe();
      };
    });
  }

  disconnect(): void {
    this.client?.deactivate();
    this.client = undefined;
    this.resubscribers.clear();
  }
}
