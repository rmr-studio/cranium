import { useEffect, useRef } from 'react';
import { useQueryClient } from '@tanstack/react-query';
import type { IMessage, StompSubscription } from '@stomp/stompjs';
import { useWebSocket } from './use-websocket';
import { useAuth } from '@/components/provider/auth-context';
import { WORKSPACE_CHANNELS, type WebSocketMessage } from '@/lib/websocket/message-types';
import { handleWebSocketMessage, invalidateAllWorkspaceQueries } from '@/lib/websocket/query-bridge';
import { logWebSocketMessage } from '@/lib/websocket/dev-logger';

/**
 * Manages STOMP subscriptions for the active workspace.
 * Subscribes to all workspace channels on mount, unsubscribes on workspace change or unmount.
 * Routes each incoming message through the dev logger and query bridge.
 */
export function useWorkspaceSubscription(workspaceId: string | undefined): void {
  const { client, connectionState } = useWebSocket();
  const { user } = useAuth();
  const queryClient = useQueryClient();

  const subscriptionsRef = useRef<StompSubscription[]>([]);
  const previousWorkspaceRef = useRef<string | undefined>(undefined);

  // ------ Subscribe/Unsubscribe on workspace or connection change ------

  useEffect(() => {
    if (!client || connectionState !== 'CONNECTED' || !workspaceId) return;

    // Unsubscribe from previous workspace
    unsubscribeAll(subscriptionsRef.current);
    subscriptionsRef.current = [];

    // If workspace changed, invalidate queries for catch-up
    if (previousWorkspaceRef.current && previousWorkspaceRef.current !== workspaceId) {
      invalidateAllWorkspaceQueries(queryClient, workspaceId);
    }
    previousWorkspaceRef.current = workspaceId;

    // Subscribe to all channels for this workspace
    const newSubscriptions = WORKSPACE_CHANNELS.map((channel) => {
      const topic = `/topic/workspace/${workspaceId}/${channel.toLowerCase()}`;
      return client.subscribe(topic, (stompMessage: IMessage) => {
        const message = parseMessage(stompMessage);
        if (!message) return;

        logWebSocketMessage(message);
        handleWebSocketMessage(queryClient, message, user?.id);
      });
    });

    subscriptionsRef.current = newSubscriptions;

    return () => {
      unsubscribeAll(subscriptionsRef.current);
      subscriptionsRef.current = [];
    };
  }, [client, connectionState, workspaceId, queryClient, user?.id]);

  // ------ Tab Visibility Catch-Up ------

  useEffect(() => {
    if (!workspaceId) return;

    function handleVisibilityChange() {
      if (document.visibilityState !== 'visible') return;
      invalidateAllWorkspaceQueries(queryClient, workspaceId!);
    }

    document.addEventListener('visibilitychange', handleVisibilityChange);
    return () => document.removeEventListener('visibilitychange', handleVisibilityChange);
  }, [queryClient, workspaceId]);

  // ------ Reconnect Catch-Up ------
  // When connectionState transitions from RECONNECTING to CONNECTED, the subscriptions
  // above are re-established (because connectionState is in the dep array), and we fire
  // a full invalidation to catch up on any missed events.

  const prevConnectionStateRef = useRef(connectionState);
  useEffect(() => {
    const wasReconnecting = prevConnectionStateRef.current === 'RECONNECTING';
    prevConnectionStateRef.current = connectionState;

    if (wasReconnecting && connectionState === 'CONNECTED' && workspaceId) {
      invalidateAllWorkspaceQueries(queryClient, workspaceId);
    }
  }, [connectionState, queryClient, workspaceId]);
}

// ------ Helpers ------

function unsubscribeAll(subscriptions: StompSubscription[]): void {
  for (const sub of subscriptions) {
    try {
      sub.unsubscribe();
    } catch {
      // Subscription may already be cleared if client disconnected
    }
  }
}

function parseMessage(stompMessage: IMessage): WebSocketMessage | null {
  try {
    const parsed = JSON.parse(stompMessage.body);
    if (
      !parsed ||
      typeof parsed.channel !== 'string' ||
      typeof parsed.operation !== 'string' ||
      typeof parsed.workspaceId !== 'string' ||
      !WORKSPACE_CHANNELS.includes(parsed.channel)
    ) {
      if (process.env.NODE_ENV === 'development') {
        console.warn('[WS] Malformed message — missing or invalid fields:', parsed);
      }
      return null;
    }
    return parsed as WebSocketMessage;
  } catch {
    if (process.env.NODE_ENV === 'development') {
      console.error('[WS] Failed to parse STOMP message:', stompMessage.body);
    }
    return null;
  }
}
