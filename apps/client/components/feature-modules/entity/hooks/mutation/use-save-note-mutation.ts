import { useAuth } from '@/components/provider/auth-context';
import { Note, CreateNoteRequest, UpdateNoteRequest } from '@/lib/types';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { toast } from 'sonner';
import { NoteService } from '../../service/note.service';
import { noteKeys } from '../query/use-notes';

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
    onError: (error) => {
      toast.error(`Failed to save note: ${error.message}`);
    },
    onSuccess: (_data, variables) => {
      queryClient.invalidateQueries({
        queryKey: noteKeys.list(workspaceId, variables.entityId),
      });
      // Invalidate entity queries so noteCount updates
      if (!variables.noteId) {
        queryClient.invalidateQueries({
          queryKey: ['entities', workspaceId],
        });
      }
    },
  });
}
