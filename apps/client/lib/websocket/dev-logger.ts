import type { WebSocketMessage } from '@/lib/websocket/message-types';

export function logWebSocketMessage(message: WebSocketMessage): void {
  if (process.env.NODE_ENV !== 'development') return;

  console.log(
    `[WS] %c${message.channel}%c ${message.operation} ${message.entityId ?? '—'} @ ${message.timestamp}`,
    'color: #7c3aed; font-weight: bold',
    'color: inherit',
    message.summary,
  );
}
