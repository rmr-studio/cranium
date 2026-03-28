import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { Note } from '@/lib/types';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';

export const noteKeys = {
  list: (workspaceId: string, entityId: string) =>
    ['notes', workspaceId, entityId] as const,
};

export function useNotes(workspaceId: string, entityId: string): AuthenticatedQueryResult<Note[]> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: noteKeys.list(workspaceId, entityId),
    queryFn: () => NoteService.getNotesForEntity(session, workspaceId, entityId),
    staleTime: 5 * 60 * 1000,
    enabled: !!entityId && !!workspaceId,
  });
}
