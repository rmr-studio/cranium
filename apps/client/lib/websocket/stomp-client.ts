import type { Client, StompConfig } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

export interface StompClientOptions {
  wsUrl: string;
  token: string;
  onConnect: () => void;
  onDisconnect: () => void;
  onStompError: (frame: { headers: Record<string, string>; body: string }) => void;
  onWebSocketClose: () => void;
  reconnectDelay?: number;
}

/**
 * Creates a STOMP client using dynamic import to avoid SSR issues.
 * @stomp/stompjs accesses WebSocket at import time — must be loaded client-side only.
 */
export async function createStompClient(options: StompClientOptions): Promise<Client> {
  const { Client: StompClient } = await import('@stomp/stompjs');

  const config: StompConfig = {
    webSocketFactory: () => new SockJS(options.wsUrl),
    connectHeaders: {
      Authorization: `Bearer ${options.token}`,
    },
    reconnectDelay: options.reconnectDelay ?? 5000,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: options.onConnect,
    onDisconnect: options.onDisconnect,
    onStompError: options.onStompError,
    onWebSocketClose: options.onWebSocketClose,
  };

  return new StompClient(config);
}

/**
 * Updates the connect headers on an existing STOMP client (e.g. after token refresh).
 */
export function updateStompToken(client: Client, token: string): void {
  client.configure({
    connectHeaders: {
      Authorization: `Bearer ${token}`,
    },
  });
}
