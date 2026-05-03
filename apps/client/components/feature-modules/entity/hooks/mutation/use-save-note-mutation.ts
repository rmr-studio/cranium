import { useAuth } from '@/components/provider/auth-context';
import { Note, CreateNoteRequest, UpdateNoteRequest } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';
import { noteKeys } from '@/components/feature-modules/entity/hooks/query/use-notes';
import { workspaceNoteKeys } from '@/components/feature-modules/entity/hooks/query/use-workspace-notes';

interface SaveNoteArgs {
  noteId?: string;
  entityId: string;
  request: CreateNoteRequest | UpdateNoteRequest;
}

export function useSaveNoteMutation(workspaceId: string) {
  const queryClient = useQueryClient();
  const { session } = useAuth();

  return useMutation<Note, Error, SaveNoteArgs>({
    mutationFn: async ({ noteId, entityId, request }) => {
      if (noteId) {
        return NoteService.updateNote(session, workspaceId, noteId, request as UpdateNoteRequest);
      }
      return NoteService.createNote(session, workspaceId, entityId, request as CreateNoteRequest);
    },
    onError: (error, variables) => {
      toast.error(variables.noteId ? 'Failed to save note' : `Failed to create note: ${error.message}`);
    },
    onSuccess: (_data, variables) => {
      if (!variables.noteId) {
        toast.success('Note created');
      }
      queryClient.invalidateQueries({
        queryKey: noteKeys.list(workspaceId, variables.entityId),
      });
      // Invalidate entity queries so knowledgeRefs updates
      if (!variables.noteId) {
        queryClient.invalidateQueries({
          queryKey: ['entities', workspaceId],
        });
      }
      // Invalidate workspace notes list
      queryClient.invalidateQueries({
        queryKey: workspaceNoteKeys.all(workspaceId),
      });
    },
  });
}
