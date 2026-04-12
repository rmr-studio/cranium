import { useAuth } from '@/components/provider/auth-context';
import { DevService } from '@/components/feature-modules/dev/service/dev.service';
import { entityKeys } from '@/components/feature-modules/entity/hooks/query/entity-query-keys';
import { TemplateInstallationResponse } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';

interface ReinstallTemplateArgs {
  templateKey: string;
}

interface ReinstallMutationContext {
  toastId: string | number;
}

export function useReinstallTemplateMutation(workspaceId: string | undefined) {
  const { session } = useAuth();
  const queryClient = useQueryClient();

  return useMutation<
    TemplateInstallationResponse,
    Error,
    ReinstallTemplateArgs,
    ReinstallMutationContext
  >({
    mutationFn: async ({ templateKey }) => {
      if (!session) throw new Error('Not authenticated');
      if (!workspaceId) throw new Error('No workspace selected');
      if (!templateKey.trim()) throw new Error('Template key required');
      return DevService.reinstallTemplate(session, workspaceId, templateKey.trim());
    },
    onMutate: () => {
      const toastId = toast.loading('Reinstalling template...');
      return { toastId };
    },
    onSuccess: (data, _vars, context) => {
      if (context) toast.dismiss(context.toastId);
      toast.success(
        `Reinstalled ${data.templateName}: ${data.entityTypesCreated} types, ${data.relationshipsCreated} relationships`,
      );
      if (workspaceId) {
        queryClient.invalidateQueries({ queryKey: entityKeys.entities.base(workspaceId) });
        queryClient.invalidateQueries({ queryKey: entityKeys.entityTypes.list(workspaceId) });
      }
    },
    onError: (error, _vars, context) => {
      if (context) toast.dismiss(context.toastId);
      toast.error(`Reinstall failed: ${error.message}`);
    },
  });
}
