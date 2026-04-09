import { DefinitionCategory, DefinitionStatus } from '@/lib/types/workspace';

export const definitionKeys = {
  definitions: {
    base: (workspaceId: string) => ['definitions', workspaceId] as const,
    list: (workspaceId: string, status?: DefinitionStatus, category?: DefinitionCategory) =>
      ['definitions', workspaceId, status ?? 'all', category ?? 'all'] as const,
  },
  definition: {
    detail: (workspaceId: string, definitionId: string) =>
      ['definition', workspaceId, definitionId] as const,
  },
} as const;
