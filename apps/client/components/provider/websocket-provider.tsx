'use client';

import { useAuth } from '@/components/provider/auth-context';
import { createAuthProvider } from '@/lib/auth';
import { createStompClient, updateStompToken } from '@/lib/websocket/stomp-client';
import type { Client } from '@stomp/stompjs';
import { createContext, useCallback, useEffect, useRef, useState } from 'react';

// ------ Connection State Machine ------
// DISCONNECTED ──activate()──→ CONNECTING ──onConnect──→ CONNECTED
//      ↑                            ↑                        │
//      │ (max retries)              │ (retry)                │ onWebSocketClose
//      │                            │                        ▼
// AUTH_FAILED ←──onStompError──  RECONNECTING ←──────────────┘

export type ConnectionState =
  | 'DISCONNECTED'
  | 'CONNECTING'
  | 'CONNECTED'
  | 'RECONNECTING'
  | 'AUTH_FAILED';

export interface WebSocketContextValue {
  connectionState: ConnectionState;
  client: Client | null;
  lastConnectedAt: Date | null;
}

export const WebSocketContext = createContext<WebSocketContextValue>({
  connectionState: 'DISCONNECTED',
  client: null,
  lastConnectedAt: null,
});

// ------ Provider Component ------

export function WebSocketProvider({ children }: { children: React.ReactNode }) {
  const { session } = useAuth();

  const [connectionState, setConnectionState] = useState<ConnectionState>('DISCONNECTED');
  const [lastConnectedAt, setLastConnectedAt] = useState<Date | null>(null);
  const [client, setClient] = useState<Client | null>(null);

  const clientRef = useRef<Client | null>(null);
  const token = session?.access_token;

  // ------ STOMP Lifecycle ------

  const initializeClient = useCallback(async () => {
    if (!token) {
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
        clientRef.current = null;
        setClient(null);
      }
      setConnectionState('DISCONNECTED');
      return;
    }

    // Tear down existing client
    if (clientRef.current?.active) {
      clientRef.current.deactivate();
    }

    if (!process.env.NEXT_PUBLIC_API_URL) {
      console.error('NEXT_PUBLIC_API_URL is not defined');
      return;
    }

    setConnectionState('CONNECTING');

    const stompClient = await createStompClient({
      wsUrl: `${process.env.NEXT_PUBLIC_API_URL}/ws`,
      token,
      onConnect: () => {
        setConnectionState('CONNECTED');
        setLastConnectedAt(new Date());
      },
      onDisconnect: () => {
        setConnectionState((prev) => (prev === 'AUTH_FAILED' ? prev : 'RECONNECTING'));
      },
      onStompError: (frame) => {
        const message = frame.headers['message'] ?? frame.body ?? '';
        const isAuthError = message.includes('401') || message.toLowerCase().includes('auth');

        if (isAuthError) {
          setConnectionState('AUTH_FAILED');
          stompClient.deactivate();
        }
      },
      onWebSocketClose: () => {
        setConnectionState((prev) => {
          if (prev === 'AUTH_FAILED') return prev;
          if (prev === 'CONNECTED') return 'RECONNECTING';
          return prev;
        });
      },
    });

    clientRef.current = stompClient;
    setClient(stompClient);
    stompClient.activate();
  }, [token]);

  // Initialize on mount / token change
  useEffect(() => {
    initializeClient();

    return () => {
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
      }
    };
  }, [initializeClient]);

  // ------ Token Refresh Handling ------

  useEffect(() => {
    const provider = createAuthProvider();
    const subscription = provider.onAuthStateChange((event, newSession) => {
      if (event !== 'TOKEN_REFRESHED' || !newSession || !clientRef.current) return;

      const currentClient = clientRef.current;
      updateStompToken(currentClient, newSession.access_token);

      // Force reconnect to use the new token
      if (currentClient.active) {
        currentClient.deactivate();
        currentClient.activate();
      }
    });

    return () => subscription.unsubscribe();
  }, []);

  return (
    <WebSocketContext.Provider value={{ connectionState, client, lastConnectedAt }}>
      {children}
    </WebSocketContext.Provider>
  );
}
