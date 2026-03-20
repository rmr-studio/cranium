import { useAuth } from '@/components/provider/auth-context';
import { Note } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import { NoteService } from '../../service/note.service';

export const noteKeys = {
  list: (workspaceId: string, entityId: string) =>
    ['notes', workspaceId, entityId] as const,
};

export function useNotes(workspaceId: string, entityId: string) {
  const { session, loading } = useAuth();

  return useQuery<Note[]>({
    queryKey: noteKeys.list(workspaceId, entityId),
    queryFn: () => NoteService.getNotesForEntity(session, workspaceId, entityId),
    staleTime: 5 * 60 * 1000,
    enabled: !!session && !!entityId && !!workspaceId && !loading,
  });
}
