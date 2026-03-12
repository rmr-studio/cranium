/**
 * Centralized query key factory for all entity-related TanStack Query keys.
 *
 * Every query hook and mutation cache operation MUST use these keys.
 * This prevents key drift and makes cache invalidation predictable.
 */
export const entityKeys = {
  entities: {
    /** Base key for broad invalidation of all entity lists in a workspace */
    base: (workspaceId: string) => ['entities', workspaceId] as const,
    /** Full key for a specific entity type's entity list */
    list: (workspaceId: string, typeId: string) =>
      ['entities', workspaceId, typeId] as const,
    /** Key for batch entity queries across multiple types */
    batch: (workspaceId: string, typeIds: string[]) =>
      ['entities', workspaceId, 'batch', typeIds] as const,
  },
  entityTypes: {
    /** Key for the full entity types list in a workspace */
    list: (workspaceId: string) => ['entityTypes', workspaceId] as const,
    /** Key for a single entity type by its key (not UUID) */
    byKey: (key: string, workspaceId: string, include?: string[]) =>
      include
        ? (['entityType', key, workspaceId, include] as const)
        : (['entityType', key, workspaceId] as const),
  },
  /** Key for semantic metadata for a specific entity type */
  semanticMetadata: (workspaceId: string, entityTypeId: string) =>
    ['semanticMetadata', workspaceId, entityTypeId] as const,
} as const;
