'use client';

import { useWorkspaceNotes } from '@/components/feature-modules/entity/hooks/query/use-workspace-notes';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { WorkspaceNote } from '@/lib/types';
import { IconColour, IconType } from '@/lib/types/common';
import { formatNoteTimestamp } from '@/lib/types/entity';
import { groupNotesByDate } from '@/lib/util/note/note-grouping.util';
import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { cn } from '@riven/utils';
import { Plus, Search, StickyNote } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useMemo, useRef, useState } from 'react';
import { Skeleton } from '../../skeleton';

export function NotesPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const [search, setSearch] = useState('');
  const [debouncedSearch, setDebouncedSearch] = useState('');
  const debounceRef = useRef<ReturnType<typeof setTimeout>>();

  useEffect(() => {
    debounceRef.current = setTimeout(() => setDebouncedSearch(search), 300);
    return () => clearTimeout(debounceRef.current);
  }, [search]);

  const { data, isLoading } = useWorkspaceNotes(
    selectedWorkspaceId,
    debouncedSearch || undefined,
  );

  const groups = useMemo(
    () => groupNotesByDate(data?.items ?? []),
    [data?.items],
  );

  const basePath = `/dashboard/workspace/${selectedWorkspaceId}/notes`;

  if (!selectedWorkspaceId) return null;

  return (
    <div className="flex flex-col gap-3">
      {/* Search */}
      <div className="relative">
        <Search className="absolute left-2.5 top-2.5 size-4 text-muted-foreground" />
        <Input
          placeholder="Search notes..."
          value={search}
          onChange={(e) => setSearch(e.target.value)}
          className="pl-9"
          aria-label="Search notes"
        />
      </div>

      {/* View All Notes */}
      <Link
        href={basePath}
        className="flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground"
      >
        <StickyNote className="size-4" />
        View All Notes
      </Link>

      {/* New Note */}
      <Button variant="default" size="sm" className="w-full" asChild>
        <Link href={basePath}>
          <Plus className="mr-1.5 size-3.5" />
          New Note
        </Link>
      </Button>

      {/* Content */}
      {isLoading ? (
        <div className="flex flex-col gap-2">
          {Array.from({ length: 3 }).map((_, i) => (
            <div key={i} className="flex flex-col gap-1.5 px-3 py-2.5">
              <Skeleton className="h-4 w-3/4 rounded" />
              <Skeleton className="h-3 w-1/2 rounded" />
            </div>
          ))}
        </div>
      ) : groups.length === 0 ? (
        search ? (
          <div className="flex flex-col items-center gap-2 py-8 text-center">
            <p className="text-sm text-muted-foreground">
              No notes matching &apos;{search}&apos;
            </p>
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
          <div className="flex flex-col items-center gap-3 py-8 text-center">
            <StickyNote className="size-8 text-muted-foreground/40" />
            <p className="text-sm text-muted-foreground">
              No notes yet. Create your first note to keep track of important
              details about your records.
            </p>
            <Button variant="default" size="sm" asChild>
              <Link href={basePath}>
                <Plus className="mr-1.5 size-3.5" />
                New Note
              </Link>
            </Button>
          </div>
        )
      ) : (
        <div className="flex flex-col gap-1">
          {groups.map((group) => (
            <div key={group.label}>
              <p
                role="heading"
                aria-level={3}
                className="mb-1 px-3 pt-2 font-mono text-xs font-bold uppercase tracking-wide text-muted-foreground"
              >
                {group.label}
              </p>
              {group.notes.map((note) => (
                <PanelNoteCard
                  key={note.id}
                  note={note}
                  workspaceId={selectedWorkspaceId}
                />
              ))}
            </div>
          ))}

        </div>
      )}
    </div>
  );
}

interface PanelNoteCardProps {
  note: WorkspaceNote;
  workspaceId: string;
}

function PanelNoteCard({ note, workspaceId }: PanelNoteCardProps) {
  return (
    <Link
      href={`/dashboard/workspace/${workspaceId}/notes/${note.id}`}
      className={cn(
        'flex items-start gap-3 rounded-md px-3 py-2.5 text-sm transition-colors',
        'text-sidebar-foreground/70 hover:bg-sidebar-accent hover:text-sidebar-foreground',
      )}
    >
      <StickyNote className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
      <div className="min-w-0 flex-1">
        <p className="truncate font-medium">{note.title || 'Untitled'}</p>
        <div className="mt-0.5 flex items-center gap-1.5 text-xs text-muted-foreground">
          <IconCell
            readonly
            type={note.entityTypeIcon as IconType}
            colour={note.entityTypeColour as IconColour}
            className="size-3"
          />
          <span className="truncate">
            {note.entityDisplayName ?? 'Unknown'}
          </span>
          <span>·</span>
          <span className="shrink-0 tabular-nums">
            {formatNoteTimestamp(note.updatedAt ?? note.createdAt)}
          </span>
        </div>
      </div>
    </Link>
  );
}
