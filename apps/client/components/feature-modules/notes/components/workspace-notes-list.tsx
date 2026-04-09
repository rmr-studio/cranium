'use client';

import { useWorkspaceNotes } from '@/components/feature-modules/entity/hooks/query/use-workspace-notes';
import { NoteRow } from '@/components/feature-modules/notes/components/workspace-notes-list-row';
import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { Skeleton } from '@/components/ui/skeleton';
import { groupNotesByDate } from '@/lib/util/note/note-grouping.util';
import { Input } from '@riven/ui/input';
import { Search, StickyNote } from 'lucide-react';
import { useEffect, useMemo, useRef, useState } from 'react';

export function WorkspaceNotesList() {
  const { data: workspace, isPending: workspacePending, isLoadingAuth } = useWorkspace();
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    debounceRef.current = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(debounceRef.current);
  }, [search]);

  const workspaceId = workspace?.id ?? '';
  const { data, isLoading: notesLoading } = useWorkspaceNotes(
    workspaceId,
    debouncedSearch || undefined,
  );

  const groups = useMemo(() => groupNotesByDate(data?.items ?? []), [data?.items]);

  if (workspacePending || isLoadingAuth) {
    return (
      <>
        <Skeleton className="mb-8 h-6 w-48" />
        <Skeleton className="mb-4 h-10 w-full max-w-md" />
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-md" />
          ))}
        </div>
      </>
    );
  }

  if (!workspace) return null;

  const basePath = `/dashboard/workspace/${workspace.id}`;

  const trail: BreadCrumbTrail[] = [
    { label: 'Home', href: '/dashboard' },
    { label: 'Workspaces', href: '/dashboard/workspace' },
    { label: workspace.name || 'Workspace', href: basePath },
    { label: 'Notes' },
  ];

  return (
    <>
      {/* Header */}
      <header className="mb-8 flex items-center justify-between">
        <BreadCrumbGroup items={trail} />
      </header>

      {/* Title + Actions */}
      <div className="mb-6 flex items-center justify-between">
        <div className="flex items-center gap-3">
          <h1 className="text-2xl font-bold tracking-tight">Notes</h1>
          {data?.totalCount != null && (
            <span className="text-sm text-muted-foreground">({data.totalCount})</span>
          )}
        </div>
      </div>

      {/* Search */}
      <div className="relative mb-6 max-w-md">
        <Search className="absolute top-2.5 left-2.5 size-4 text-muted-foreground" />
        <Input
          placeholder="Search notes..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
          aria-label="Search notes"
        />
      </div>

      {/* Content */}
      {notesLoading ? (
        <div className="space-y-3">
          {Array.from({ length: 5 }).map((_, i) => (
            <Skeleton key={i} className="h-16 w-full rounded-md" />
          ))}
        </div>
      ) : groups.length === 0 ? (
        search ? (
          <div className="flex flex-col items-center gap-2 py-16 text-center">
            <p className="text-sm text-muted-foreground">No notes matching &apos;{search}&apos;</p>
            <button
              onClick={() => {
                setSearch('');
                setDebouncedSearch('');
              }}
              className="text-sm text-primary hover:underline"
            >
              Clear search
            </button>
          </div>
        ) : (
          <div className="flex flex-col items-center gap-4 py-16 text-center">
            <StickyNote className="size-12 text-muted-foreground/40" />
            <div>
              <p className="text-base font-medium">No notes yet</p>
              <p className="mt-1 text-sm text-muted-foreground">
                Notes can be created from within an entity&apos;s detail page.
              </p>
            </div>
          </div>
        )
      ) : (
        <div className="space-y-6">
          {groups.map((group) => (
            <div key={group.label}>
              <p
                role="heading"
                aria-level={3}
                className="mb-2 font-mono text-xs font-bold tracking-wide text-muted-foreground uppercase"
              >
                {group.label} ({group.notes.length})
              </p>
              <div className="divide-y rounded-lg border">
                {group.notes.map((note) => (
                  <NoteRow key={note.id} note={note} workspaceId={workspace.id} />
                ))}
              </div>
            </div>
          ))}
        </div>
      )}
    </>
  );
}
