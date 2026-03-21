import { useAuth } from '@/components/provider/auth-context';
import { WorkspaceNote } from '@/lib/types';
import { useQuery } from '@tanstack/react-query';
import { NoteService } from '@/components/feature-modules/entity/service/note.service';
import { workspaceNoteKeys } from '@/components/feature-modules/entity/hooks/query/use-workspace-notes';

export function useNote(workspaceId: string, noteId: string) {
  const { session, loading } = useAuth();

  return useQuery<WorkspaceNote>({
    queryKey: workspaceNoteKeys.detail(workspaceId, noteId),
    queryFn: () => NoteService.getWorkspaceNote(session, workspaceId, noteId),
    staleTime: 5 * 60 * 1000,
    enabled: !!session && !!workspaceId && !!noteId && !loading,
  });
}
