'use client';

import { useAuth } from '@/components/provider/auth-context';
import { createStompClient } from '@/lib/websocket/stomp-client';
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
  const initGenRef = useRef(0);
  const intentionalDisconnectRef = useRef(false);
  const token = session?.access_token;

  // ------ STOMP Lifecycle ------

  const initializeClient = useCallback(async () => {
    if (!token) {
      intentionalDisconnectRef.current = true;
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
        clientRef.current = null;
        setClient(null);
      }
      setConnectionState('DISCONNECTED');
      setLastConnectedAt(null);
      return;
    }

    // Increment generation to guard against stale async resolutions
    const generation = ++initGenRef.current;
    intentionalDisconnectRef.current = false;

    // Tear down existing client
    if (clientRef.current?.active) {
      clientRef.current.deactivate();
    }

    if (!process.env.NEXT_PUBLIC_API_URL) {
      console.error('NEXT_PUBLIC_API_URL is not defined');
      return;
    }

    setConnectionState('CONNECTING');

    let stompClient: Client | undefined;
    try {
      stompClient = await createStompClient({
        wsUrl: `${process.env.NEXT_PUBLIC_API_URL}/ws`,
        token,
        onConnect: () => {
          setConnectionState('CONNECTED');
          setLastConnectedAt(new Date());
        },
        onDisconnect: () => {
          if (intentionalDisconnectRef.current) return;
          setConnectionState((prev) => (prev === 'AUTH_FAILED' ? prev : 'RECONNECTING'));
        },
        onStompError: (frame) => {
          const message = frame.headers['message'] ?? frame.body ?? '';
          const isAuthError = message.includes('401') || message.toLowerCase().includes('auth');

          if (isAuthError) {
            setConnectionState('AUTH_FAILED');
            stompClient?.deactivate();
          }
        },
        onWebSocketClose: () => {
          if (intentionalDisconnectRef.current) return;
          setConnectionState((prev) => {
            if (prev === 'AUTH_FAILED') return prev;
            if (prev === 'CONNECTED') return 'RECONNECTING';
            return prev;
          });
        },
      });

      // Stale guard: if a newer initializeClient call started, discard this result
      if (generation !== initGenRef.current) {
        stompClient.deactivate();
        return;
      }

      clientRef.current = stompClient;
      setClient(stompClient);
      stompClient.activate();
    } catch (error) {
      console.error('Failed to create STOMP client:', error);

      if (stompClient) {
        stompClient.deactivate();
      }

      // Only update state if this is still the current generation
      if (generation === initGenRef.current) {
        clientRef.current = null;
        setClient(null);
        if (!intentionalDisconnectRef.current) {
          setConnectionState('DISCONNECTED');
        }
      }
    }
  }, [token]);

  // Initialize on mount / token change
  useEffect(() => {
    initializeClient();

    return () => {
      intentionalDisconnectRef.current = true;
      if (clientRef.current?.active) {
        clientRef.current.deactivate();
      }
    };
  }, [initializeClient]);

  return (
    <WebSocketContext.Provider value={{ connectionState, client, lastConnectedAt }}>
      {children}
    </WebSocketContext.Provider>
  );
}
