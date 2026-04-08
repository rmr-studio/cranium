export const integrationKeys = {
  definitions: {
    all: ['integrations'] as const,
  },
  connections: {
    byWorkspace: (workspaceId: string) => ['integrations', 'connections', workspaceId] as const,
  },
};
