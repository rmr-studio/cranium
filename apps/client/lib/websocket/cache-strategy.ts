import type { WebSocketChannel, OperationType } from '@/lib/websocket/message-types';

export type CacheAction = 'invalidate' | 'setQueryData';

export interface CacheStrategyEntry {
  action: CacheAction;
  suppressSelf: boolean;
}

const strategyMap: Record<WebSocketChannel, Record<OperationType, CacheStrategyEntry>> = {
  ENTITIES: {
    CREATE: { action: 'invalidate', suppressSelf: true },
    READ: { action: 'invalidate', suppressSelf: false },
    UPDATE: { action: 'invalidate', suppressSelf: true },
    DELETE: { action: 'invalidate', suppressSelf: true },
    RESTORE: { action: 'invalidate', suppressSelf: true },
  },
  NOTIFICATIONS: {
    CREATE: { action: 'invalidate', suppressSelf: false },
    READ: { action: 'invalidate', suppressSelf: false },
    UPDATE: { action: 'setQueryData', suppressSelf: false },
    DELETE: { action: 'invalidate', suppressSelf: false },
    RESTORE: { action: 'invalidate', suppressSelf: false },
  },
  BLOCKS: {
    CREATE: { action: 'invalidate', suppressSelf: true },
    READ: { action: 'invalidate', suppressSelf: false },
    UPDATE: { action: 'invalidate', suppressSelf: true },
    DELETE: { action: 'invalidate', suppressSelf: true },
    RESTORE: { action: 'invalidate', suppressSelf: true },
  },
  WORKFLOWS: {
    CREATE: { action: 'invalidate', suppressSelf: false },
    READ: { action: 'invalidate', suppressSelf: false },
    UPDATE: { action: 'invalidate', suppressSelf: false },
    DELETE: { action: 'invalidate', suppressSelf: false },
    RESTORE: { action: 'invalidate', suppressSelf: false },
  },
  WORKSPACE: {
    CREATE: { action: 'invalidate', suppressSelf: false },
    READ: { action: 'invalidate', suppressSelf: false },
    UPDATE: { action: 'invalidate', suppressSelf: false },
    DELETE: { action: 'invalidate', suppressSelf: false },
    RESTORE: { action: 'invalidate', suppressSelf: false },
  },
};

export function getStrategy(channel: WebSocketChannel, operation: OperationType): CacheStrategyEntry {
  return strategyMap[channel][operation];
}
