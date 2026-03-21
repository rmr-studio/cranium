import { useAuth } from '@/components/provider/auth-context';
import { CursorPageWorkspaceNote } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';

export const workspaceNoteKeys = {
  all: (workspaceId: string) => ['workspace-notes', workspaceId] as const,
  list: (workspaceId: string, search?: string) =>
    ['workspace-notes', workspaceId, 'list', search ?? ''] as const,
  detail: (workspaceId: string, noteId: string) =>
    ['workspace-notes', workspaceId, 'detail', noteId] as const,
};

export function useWorkspaceNotes(workspaceId: string, search?: string) {
  const { session, loading } = useAuth();

  return useQuery<CursorPageWorkspaceNote>({
    queryKey: workspaceNoteKeys.list(workspaceId, search),
    queryFn: () => NoteService.getWorkspaceNotes(session, workspaceId, search),
    staleTime: 5 * 60 * 1000,
    enabled: !!session && !!workspaceId && !loading,
  });
}
