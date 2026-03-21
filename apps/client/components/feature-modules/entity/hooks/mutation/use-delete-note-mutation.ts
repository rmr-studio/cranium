import { useAuth } from '@/components/provider/auth-context';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';
import { noteKeys } from '@/components/feature-modules/entity/hooks/query/use-notes';

interface DeleteNoteArgs {
  noteId: string;
  entityId: string;
}

interface DeleteNoteContext {
  toastId: string | number;
}

export function useDeleteNoteMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  const { session } = useAuth();

  return useMutation<void, Error, DeleteNoteArgs, DeleteNoteContext>({
    mutationFn: async ({ noteId }) => {
      return NoteService.deleteNote(session, workspaceId, noteId);
    },
    onMutate: () => {
      return { toastId: toast.loading('Deleting note...') };
    },
    onError: (error, _variables, context) => {
      toast.dismiss(context?.toastId);
      toast.error(`Failed to delete note: ${error.message}`);
    },
    onSuccess: (_data, variables, context) => {
      toast.dismiss(context?.toastId);
      toast.success('Note deleted');

      queryClient.invalidateQueries({
        queryKey: noteKeys.list(workspaceId, variables.entityId),
      });
      // Invalidate entity queries so noteCount updates
      queryClient.invalidateQueries({
        queryKey: ['entities', workspaceId],
      });
      // Invalidate workspace notes list
      queryClient.invalidateQueries({
        queryKey: ['workspace-notes', workspaceId],
      });
    },
  });
}
