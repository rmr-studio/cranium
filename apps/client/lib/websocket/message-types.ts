export const WORKSPACE_CHANNELS = [
  'ENTITIES',
  'BLOCKS',
  'WORKFLOWS',
  'NOTIFICATIONS',
  'WORKSPACE',
] as const;

export type WebSocketChannel = (typeof WORKSPACE_CHANNELS)[number];

export type OperationType = 'CREATE' | 'READ' | 'UPDATE' | 'DELETE' | 'RESTORE';

export interface WebSocketMessage {
  channel: WebSocketChannel;
  operation: OperationType;
  workspaceId: string;
  entityId: string | null;
  userId: string;
  timestamp: string;
  summary: Record<string, unknown>;
}
