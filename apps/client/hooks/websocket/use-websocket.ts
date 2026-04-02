import { WebSocketContext } from '@/components/provider/websocket-provider';
import type { WebSocketContextValue } from '@/components/provider/websocket-provider';
import { useContext } from 'react';

export function useWebSocket(): WebSocketContextValue {
  return useContext(WebSocketContext);
}
