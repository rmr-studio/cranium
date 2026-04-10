'use client';

import { useDeleteNoteMutation } from '@/components/feature-modules/entity/hooks/mutation/use-delete-note-mutation';
import { useSaveNoteMutation } from '@/components/feature-modules/entity/hooks/mutation/use-save-note-mutation';
import { useEntityTypeByKey } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { useNote } from '@/components/feature-modules/entity/hooks/query/use-note';
import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { Skeleton } from '@/components/ui/skeleton';
import { extractNoteTitle, formatNoteTimestamp } from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import { PartialBlock } from '@blocknote/core';
import { Button } from '@riven/ui/button';
import { toTitleCase } from '@riven/utils';
import { ArrowLeft, StickyNote, Trash2 } from 'lucide-react';
import dynamic from 'next/dynamic';
import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { Component, ReactNode, useCallback, useEffect, useRef, useState } from 'react';

const BlockEditor = dynamic(
  () => import('@/components/ui/block-editor').then((m) => m.BlockEditor),
  {
    ssr: false,
    loading: () => <div className="min-h-96 flex-1 animate-pulse rounded-md bg-muted/30" />,
  },
);

// ============================================================================
// Error Boundary
// ============================================================================

interface ErrorBoundaryProps {
  fallback: ReactNode;
  children: ReactNode;
}

interface ErrorBoundaryState {
  hasError: boolean;
}

class EditorErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  constructor(props: ErrorBoundaryProps) {
    super(props);
    this.state = { hasError: false };
  }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { hasError: true };
  }

  componentDidCatch(error: Error) {
    console.error('BlockEditor crashed:', error);
  }

  render() {
    if (this.state.hasError) {
      return this.props.fallback;
    }
    return this.props.children;
  }
}

// ============================================================================
// NoteEditor
// ============================================================================

export function NoteEditor() {
  const { data: workspace } = useWorkspace();
  const params = useParams<{ workspaceId: string; noteId: string }>();
  const router = useRouter();
  const { workspaceId, noteId } = params;

  const { data: note, isLoading, isError } = useNote(workspaceId, noteId);
  const { mutate: saveNote } = useSaveNoteMutation(workspaceId);
  const { mutate: deleteNote } = useDeleteNoteMutation(workspaceId);

  const { data: type } = useEntityTypeByKey(note?.entityTypeKey, workspaceId);

  const [lastSaved, setLastSaved] = useState<Date | null>(null);

  // Auto-save debounce
  const debouncedSave = useRef(
    debounce((content: PartialBlock[], title: string) => {
      if (!note) return;
      saveNote(
        {
          noteId: note.id,
          entityId: note.entityId,
          request: { content: content as Array<{ [key: string]: object }>, title },
        },
        {
          onSuccess: () => setLastSaved(new Date()),
        },
      );
    }, 2500),
  ).current;

  // Flush on unmount
  useEffect(() => {
    return () => {
      debouncedSave.flush();
    };
  }, [debouncedSave]);

  const handleChange = useCallback(
    (blocks: PartialBlock[]) => {
      const title = extractNoteTitle(blocks);
      debouncedSave(blocks, title);
    },
    [debouncedSave],
  );

  const handleDelete = useCallback(() => {
    if (!note) return;
    if (!window.confirm('Delete this note?')) return;
    debouncedSave.cancel();
    deleteNote(
      { noteId: note.id, entityId: note.entityId },
      {
        onSuccess: () => {
          router.push(`/dashboard/workspace/${workspaceId}/notes`);
        },
      },
    );
  }, [note, deleteNote, debouncedSave, router, workspaceId]);

  const basePath = `/dashboard/workspace/${workspaceId}`;

  // Loading state
  if (isLoading) {
    return (
      <div className="mx-auto max-w-3xl px-6 py-6">
        <Skeleton className="mb-4 h-5 w-64" />
        <Skeleton className="mb-2 h-8 w-96" />
        <Skeleton className="mb-6 h-4 w-32" />
        <Skeleton className="h-96 w-full rounded-md" />
      </div>
    );
  }

  // Not found / error
  if (isError || !note) {
    return (
      <div className="flex flex-col items-center gap-4 py-24 text-center">
        <StickyNote className="size-12 text-muted-foreground/40" />
        <div>
          <p className="text-base font-medium">Note not found</p>
          <p className="mt-1 text-sm text-muted-foreground">
            This note may have been deleted or you don&apos;t have access.
          </p>
        </div>
        <Button variant="outline" size="sm" asChild>
          <Link href={`${basePath}/notes`}>
            <ArrowLeft className="mr-1.5 size-3.5" />
            Back to Notes
          </Link>
        </Button>
      </div>
    );
  }
  const trail: BreadCrumbTrail[] = [
    { label: 'Home', href: '/dashboard' },
    { label: 'Workspaces', href: '/dashboard/workspace', truncate: true },
    {
      label: workspace?.name || 'Workspace',
      href: `/dashboard/workspace/${workspaceId}`,
      truncate: true,
    },
    { label: 'Notes', href: `${basePath}/notes` },
    {
      label: type?.name.plural ?? toTitleCase(note.entityTypeKey ?? 'Entity'),
      href: `/dashboard/workspace/${workspaceId}/entity/${note.entityTypeKey}`,
    },
    {
      label: note.entityDisplayName ?? 'Unknown',
      href: `${basePath}/entity/${note.entityTypeKey}`,
    },
  ];

  const displayTimestamp = lastSaved
    ? `Edited ${formatNoteTimestamp(lastSaved)}`
    : note.updatedAt
      ? `Edited ${formatNoteTimestamp(note.updatedAt)}`
      : note.createdAt
        ? `Created ${formatNoteTimestamp(note.createdAt)}`
        : '';

  return (
    <div className="mx-auto">
      {/* Header */}
      <div className="mb-6">
        {/* Breadcrumb + Delete */}
        <div className="mb-4 flex items-center justify-between">
          <BreadCrumbGroup items={trail} />
          <Button
            variant="ghost"
            size="sm"
            className="text-destructive hover:bg-destructive/10"
            onClick={handleDelete}
          >
            <Trash2 className="mr-1.5 size-3.5" />
            Delete
          </Button>
        </div>

        {/* Title */}
        <h1 className="text-2xl font-bold tracking-tight">{note.title || 'Untitled'}</h1>

        {/* Timestamp */}
        {displayTimestamp && (
          <p className="mt-1 text-xs text-muted-foreground">{displayTimestamp}</p>
        )}
      </div>

      {/* Editor */}
      <EditorErrorBoundary
        fallback={
          <div className="rounded-md border border-destructive/20 bg-destructive/5 p-4">
            <p className="text-sm font-medium text-destructive">Could not load note content</p>
            <pre className="mt-2 max-h-40 overflow-auto text-xs text-muted-foreground">
              {JSON.stringify(note.content, null, 2)}
            </pre>
          </div>
        }
      >
        <BlockEditor
          key={note.id}
          initialContent={note.content as PartialBlock[]}
          onChange={handleChange}
        />
      </EditorErrorBoundary>
    </div>
  );
}
