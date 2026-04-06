import type { WebSocketChannel, OperationType } from '@/lib/websocket/message-types';

export type CacheAction = 'invalidate' | 'setQueryData';

export interface CacheStrategyEntry {
  action: CacheAction;
  suppressSelf: boolean;
}

const DEFAULT: CacheStrategyEntry = { action: 'invalidate', suppressSelf: false };

// Channels where mutations from the current user are suppressed (optimistic updates handle them)
const SUPPRESS_SELF_MUTATIONS: Set<WebSocketChannel> = new Set(['ENTITIES', 'BLOCKS']);
const MUTATION_OPS: Set<OperationType> = new Set(['CREATE', 'UPDATE', 'DELETE', 'RESTORE']);

// Per-channel overrides for specific operations
const OVERRIDES: Partial<Record<WebSocketChannel, Partial<Record<OperationType, CacheStrategyEntry>>>> = {
  NOTIFICATIONS: {
    UPDATE: { action: 'setQueryData', suppressSelf: false },
  },
};

export function getStrategy(channel: WebSocketChannel, operation: OperationType): CacheStrategyEntry {
  const override = OVERRIDES[channel]?.[operation];
  if (override) return override;

  if (SUPPRESS_SELF_MUTATIONS.has(channel) && MUTATION_OPS.has(operation)) {
    return { action: 'invalidate', suppressSelf: true };
  }

  return DEFAULT;
}
