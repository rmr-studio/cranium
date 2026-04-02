import type { QueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import type { WebSocketMessage, WebSocketChannel } from './message-types';
import { getStrategy } from './cache-strategy';

// ------ Channel → Query Key Prefix Mapping ------

const channelQueryKeyPrefixes: Record<WebSocketChannel, string[]> = {
  ENTITIES: ['entities'],
  BLOCKS: ['blocks-hydration'],
  WORKFLOWS: ['workflows'],
  NOTIFICATIONS: ['notifications'],
  WORKSPACE: ['workspace'],
};

// Entity events also affect entity type queries (e.g. entity counts)
const SUPPLEMENTARY_INVALIDATIONS: Partial<Record<WebSocketChannel, string[][]>> = {
  ENTITIES: [['entityTypes']],
};

// ------ Toast Rate Limiting ------

const TOAST_WINDOW_MS = 5000;
const TOAST_MAX_PER_WINDOW = 3;

let toastTimestamps: number[] = [];
let batchedCount = 0;
let batchToastTimeout: ReturnType<typeof setTimeout> | null = null;

function showRateLimitedToast(message: string): void {
  const now = Date.now();
  toastTimestamps = toastTimestamps.filter((t) => now - t < TOAST_WINDOW_MS);

  if (toastTimestamps.length < TOAST_MAX_PER_WINDOW) {
    toastTimestamps.push(now);
    toast.info(message);
    return;
  }

  batchedCount++;
  if (batchToastTimeout) clearTimeout(batchToastTimeout);
  batchToastTimeout = setTimeout(() => {
    if (batchedCount > 0) {
      toast.info(`${batchedCount} more change${batchedCount > 1 ? 's' : ''} from collaborators`);
      batchedCount = 0;
    }
    batchToastTimeout = null;
  }, 1000);
}

// ------ Toast Formatting ------

const TOAST_CHANNELS: Set<WebSocketChannel> = new Set(['ENTITIES', 'NOTIFICATIONS']);
const TOAST_OPERATIONS_BY_CHANNEL: Partial<Record<WebSocketChannel, Set<string>>> = {
  ENTITIES: new Set(['CREATE', 'UPDATE', 'DELETE']),
  NOTIFICATIONS: new Set(['CREATE']),
};

function formatOperationVerb(operation: string): string {
  switch (operation) {
    case 'CREATE':
      return 'created';
    case 'UPDATE':
      return 'updated';
    case 'DELETE':
      return 'deleted';
    default:
      return operation.toLowerCase();
  }
}

function formatToastMessage(message: WebSocketMessage): string | null {
  if (!TOAST_CHANNELS.has(message.channel)) return null;
  if (!TOAST_OPERATIONS_BY_CHANNEL[message.channel]?.has(message.operation)) return null;

  const displayName = (message.summary.userDisplayName as string) ?? 'Someone';
  const verb = formatOperationVerb(message.operation);

  if (message.channel === 'ENTITIES') {
    const typeName = (message.summary.entityTypeName as string) ?? 'item';
    return `${displayName} ${verb} a ${typeName}`;
  }

  if (message.channel === 'NOTIFICATIONS') {
    const title = (message.summary.title as string) ?? 'notification';
    return `New notification: ${title}`;
  }

  return null;
}

// ------ Main Bridge Function ------

export function handleWebSocketMessage(
  queryClient: QueryClient,
  message: WebSocketMessage,
  currentUserId: string | undefined,
): void {
  const strategy = getStrategy(message.channel, message.operation);

  // Self-event suppression
  if (strategy.suppressSelf && message.userId === currentUserId) {
    return;
  }

  // Apply cache strategy (fall back to invalidation for unimplemented actions like setQueryData)
  const prefix = channelQueryKeyPrefixes[message.channel];
  queryClient.invalidateQueries({
    queryKey: [prefix[0], message.workspaceId],
    refetchType: 'active',
  });

  // Supplementary invalidations (e.g. entity type counts)
  const supplementary = SUPPLEMENTARY_INVALIDATIONS[message.channel];
  if (supplementary) {
    for (const key of supplementary) {
      queryClient.invalidateQueries({
        queryKey: [...key, message.workspaceId],
        refetchType: 'active',
      });
    }
  }

  // Toast for remote changes
  if (message.userId !== currentUserId) {
    const toastMessage = formatToastMessage(message);
    if (toastMessage) {
      showRateLimitedToast(toastMessage);
    }
  }
}

/**
 * Invalidates all workspace-scoped queries. Called on reconnect to catch up
 * on events missed during the disconnect window.
 */
export function invalidateAllWorkspaceQueries(queryClient: QueryClient, workspaceId: string): void {
  for (const prefix of Object.values(channelQueryKeyPrefixes)) {
    queryClient.invalidateQueries({
      queryKey: [prefix[0], workspaceId],
      refetchType: 'active',
    });
  }

  // Also invalidate supplementary query keys (e.g. entityTypes for entity count consistency)
  for (const keys of Object.values(SUPPLEMENTARY_INVALIDATIONS)) {
    if (!keys) continue;
    for (const key of keys) {
      queryClient.invalidateQueries({
        queryKey: [...key, workspaceId],
        refetchType: 'active',
      });
    }
  }
}
