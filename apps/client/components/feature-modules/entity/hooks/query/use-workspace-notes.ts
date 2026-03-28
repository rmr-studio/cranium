import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { CursorPageWorkspaceNote } from '@/lib/types';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';

export const workspaceNoteKeys = {
  all: (workspaceId: string) => ['workspace-notes', workspaceId] as const,
  list: (workspaceId: string, search?: string) =>
    ['workspace-notes', workspaceId, 'list', search ?? ''] as const,
  detail: (workspaceId: string, noteId: string) =>
    ['workspace-notes', workspaceId, 'detail', noteId] as const,
};

export function useWorkspaceNotes(workspaceId: string, search?: string): AuthenticatedQueryResult<CursorPageWorkspaceNote> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: workspaceNoteKeys.list(workspaceId, search),
    queryFn: () => NoteService.getWorkspaceNotes(session, workspaceId, search),
    staleTime: 5 * 60 * 1000,
    enabled: !!workspaceId,
  });
}
