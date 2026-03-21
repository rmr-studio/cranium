import { useAuth } from '@/components/provider/auth-context';
import { useAuthenticatedQuery } from '@/hooks/query/use-authenticated-query';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import { WorkspaceNote } from '@/lib/types';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';
import { workspaceNoteKeys } from '@/components/feature-modules/entity/hooks/query/use-workspace-notes';

export function useNote(workspaceId: string, noteId: string): AuthenticatedQueryResult<WorkspaceNote> {
  const { session } = useAuth();
  return useAuthenticatedQuery({
    queryKey: workspaceNoteKeys.detail(workspaceId, noteId),
    queryFn: () => NoteService.getWorkspaceNote(session, workspaceId, noteId),
    staleTime: 5 * 60 * 1000,
    enabled: !!workspaceId && !!noteId,
  });
}
