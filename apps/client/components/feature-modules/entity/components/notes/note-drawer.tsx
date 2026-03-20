'use client';

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Note } from '@/lib/types';
import { extractNoteTitle, formatNoteTimestamp } from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import { useNotes } from '@/components/feature-modules/entity/hooks/query/use-notes';
import { useSaveNoteMutation } from '@/components/feature-modules/entity/hooks/mutation/use-save-note-mutation';
import { useDeleteNoteMutation } from '@/components/feature-modules/entity/hooks/mutation/use-delete-note-mutation';
import { Button } from '@riven/ui/button';
import { ChevronLeft, Plus, StickyNote, Trash2 } from 'lucide-react';
import { Component, FC, ReactNode, useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { PartialBlock } from '@blocknote/core';
import dynamic from 'next/dynamic';
import { cn } from '@riven/utils';

const BlockEditor = dynamic(
  () => import('@/components/ui/block-editor').then((m) => m.BlockEditor),
  { ssr: false, loading: () => <div className="flex-1 animate-pulse bg-muted/30" /> },
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
// NoteDrawer
// ============================================================================

export interface NoteDrawerProps {
  open: boolean;
  onClose: () => void;
  entityId: string;
  workspaceId: string;
}

export const NoteDrawer: FC<NoteDrawerProps> = ({
  open,
  onClose,
  entityId,
  workspaceId,
}) => {
  const { data: notes = [], isLoading } = useNotes(workspaceId, entityId);
  const { mutate: saveNote } = useSaveNoteMutation(workspaceId);
  const { mutate: deleteNote } = useDeleteNoteMutation(workspaceId);

  const [activeNoteId, setActiveNoteId] = useState<string | null>(null);
  const activeNote = useMemo(() => notes.find((n) => n.id === activeNoteId), [notes, activeNoteId]);

  // Track whether mutations happened for cache invalidation on close
  const mutatedRef = useRef(false);

  // Auto-save debounce
  const debouncedSave = useRef(
    debounce((noteId: string, content: PartialBlock[], title: string) => {
      saveNote({
        noteId,
        entityId,
        request: { content: content as any[], title },
      });
    }, 2500),
  ).current;

  // Flush on unmount
  useEffect(() => {
    return () => {
      debouncedSave.flush();
    };
  }, [debouncedSave]);

  const handleClose = useCallback(() => {
    debouncedSave.flush();
    setActiveNoteId(null);
    onClose();
  }, [debouncedSave, onClose]);

  const handleChange = useCallback(
    (blocks: PartialBlock[]) => {
      if (!activeNoteId) return;
      const title = extractNoteTitle(blocks);
      debouncedSave(activeNoteId, blocks, title);
    },
    [activeNoteId, debouncedSave],
  );

  const handleCreateNote = useCallback(() => {
    mutatedRef.current = true;
    const emptyContent = [
      {
        type: 'paragraph',
        props: { textColor: 'default', backgroundColor: 'default', textAlignment: 'left' },
        content: [],
        children: [],
      },
    ];

    saveNote(
      {
        entityId,
        request: { content: emptyContent as any[] },
      },
      {
        onSuccess: (note) => {
          setActiveNoteId(note.id);
        },
      },
    );
  }, [entityId, saveNote]);

  const handleDeleteNote = useCallback(
    (noteId: string) => {
      mutatedRef.current = true;
      debouncedSave.cancel();
      deleteNote(
        { noteId, entityId },
        {
          onSuccess: () => {
            if (activeNoteId === noteId) {
              setActiveNoteId(null);
            }
          },
        },
      );
    },
    [deleteNote, entityId, activeNoteId, debouncedSave],
  );

  const handleBack = useCallback(() => {
    debouncedSave.flush();
    setActiveNoteId(null);
  }, [debouncedSave]);

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && handleClose()}>
      <SheetContent side="right" className="flex w-full flex-col sm:max-w-2xl">
        {activeNote ? (
          // ============ Active note editor view ============
          <>
            <SheetHeader className="flex-row items-center gap-2 pr-8">
              <Button
                variant="ghost"
                size="icon"
                className="size-8"
                onClick={handleBack}
              >
                <ChevronLeft className="size-4" />
                <span className="sr-only">Back to notes</span>
              </Button>
              <div className="min-w-0 flex-1">
                <SheetTitle className="truncate">
                  {activeNote.title || 'Untitled'}
                </SheetTitle>
                <SheetDescription className="text-xs">
                  Created {formatNoteTimestamp(activeNote.createdAt)}
                  {activeNote.updatedAt &&
                    activeNote.createdAt &&
                    activeNote.updatedAt.getTime() !== activeNote.createdAt.getTime() &&
                    ` · Updated ${formatNoteTimestamp(activeNote.updatedAt)}`}
                </SheetDescription>
              </div>
              <Button
                variant="ghost"
                size="icon"
                className="size-8 text-destructive hover:bg-destructive/10"
                onClick={() => handleDeleteNote(activeNote.id)}
              >
                <Trash2 className="size-4" />
                <span className="sr-only">Delete note</span>
              </Button>
            </SheetHeader>

            <div className="flex-1 overflow-y-auto">
              <EditorErrorBoundary
                fallback={
                  <div className="rounded-md border border-destructive/20 bg-destructive/5 p-4">
                    <p className="text-sm font-medium text-destructive">
                      Could not load note content
                    </p>
                    <pre className="mt-2 max-h-40 overflow-auto text-xs text-muted-foreground">
                      {JSON.stringify(activeNote.content, null, 2)}
                    </pre>
                  </div>
                }
              >
                <BlockEditor
                  key={activeNote.id}
                  initialContent={activeNote.content as PartialBlock[]}
                  onChange={handleChange}
                />
              </EditorErrorBoundary>
            </div>
          </>
        ) : (
          // ============ Timeline / note list view ============
          <>
            <SheetHeader>
              <SheetTitle className="flex items-center gap-2">
                <StickyNote className="size-4" />
                Notes
                {notes.length > 0 && (
                  <span className="text-sm font-normal text-muted-foreground">
                    ({notes.length})
                  </span>
                )}
              </SheetTitle>
              <SheetDescription>All notes for this entity</SheetDescription>
            </SheetHeader>

            <div className="flex-1 overflow-y-auto">
              {isLoading ? (
                <div className="space-y-3 p-2">
                  {[1, 2, 3].map((i) => (
                    <div key={i} className="h-16 animate-pulse rounded-md bg-muted/30" />
                  ))}
                </div>
              ) : notes.length === 0 ? (
                <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
                  <StickyNote className="size-8 text-muted-foreground/40" />
                  <p className="text-sm text-muted-foreground">No notes yet</p>
                  <Button variant="outline" size="sm" onClick={handleCreateNote}>
                    <Plus className="mr-1.5 size-3.5" />
                    Add first note
                  </Button>
                </div>
              ) : (
                <div className="space-y-1 p-1">
                  {notes.map((note) => (
                    <NoteCard
                      key={note.id}
                      note={note}
                      onClick={() => setActiveNoteId(note.id)}
                      onDelete={() => handleDeleteNote(note.id)}
                    />
                  ))}
                </div>
              )}
            </div>

            {notes.length > 0 && (
              <div className="border-t pt-3">
                <Button
                  variant="outline"
                  size="sm"
                  className="w-full"
                  onClick={handleCreateNote}
                >
                  <Plus className="mr-1.5 size-3.5" />
                  New note
                </Button>
              </div>
            )}
          </>
        )}
      </SheetContent>
    </Sheet>
  );
};

// ============================================================================
// NoteCard
// ============================================================================

interface NoteCardProps {
  note: Note;
  onClick: () => void;
  onDelete: () => void;
}

const NoteCard: FC<NoteCardProps> = ({ note, onClick, onDelete }) => {
  return (
    <div
      role="button"
      tabIndex={0}
      className={cn(
        'group flex w-full cursor-pointer items-start gap-3 rounded-md px-3 py-2.5 text-left transition-colors',
        'hover:bg-muted/50',
      )}
      onClick={onClick}
      onKeyDown={(e) => { if (e.key === 'Enter' || e.key === ' ') onClick(); }}
    >
      <StickyNote className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {note.title || 'Untitled'}
        </p>
        <p className="text-xs text-muted-foreground">
          {formatNoteTimestamp(note.updatedAt ?? note.createdAt)}
        </p>
      </div>
      <Button
        variant="ghost"
        size="icon"
        className="size-7 shrink-0 text-destructive opacity-0 transition-opacity hover:bg-destructive/10 group-hover:opacity-100"
        onClick={(e) => {
          e.stopPropagation();
          onDelete();
        }}
      >
        <Trash2 className="size-3.5" />
        <span className="sr-only">Delete</span>
      </Button>
    </div>
  );
};
