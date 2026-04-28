import { useAuth } from '@/components/provider/auth-context';
import { DevService } from '@/components/feature-modules/dev/service/dev.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
import { DevSeedResponse } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

export function useSeedWorkspaceMutation(workspaceId: string | undefined) {
  const { session } = useAuth();
  const queryClient = useQueryClient();

  return useMutation<DevSeedResponse, Error>({
    mutationFn: async () => {
      if (!session) throw new Error('Not authenticated');
      if (!workspaceId) throw new Error('No workspace selected');
      return DevService.seedWorkspace(session, workspaceId);
    },
    onMutate: () => {
      toast.loading('Seeding workspace...', { id: 'dev-seed' });
    },
    onSuccess: (data) => {
      toast.dismiss('dev-seed');
      if (data.alreadySeeded) {
        toast.info('Workspace already seeded');
      } else {
        toast.success(
          `Seeded ${data.entitiesCreated} entities, ${data.relationshipsCreated} relationships`,
        );
      }
      if (workspaceId) {
        queryClient.invalidateQueries({ queryKey: entityKeys.entities.base(workspaceId) });
        queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });
      }
    },
    onError: (error) => {
      toast.dismiss('dev-seed');
      toast.error(`Seed failed: ${error.message}`);
    },
  });
}
