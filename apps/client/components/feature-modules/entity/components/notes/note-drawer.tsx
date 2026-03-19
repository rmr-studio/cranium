'use client';

import {
  Sheet,
  SheetContent,
  SheetDescription,
  SheetHeader,
  SheetTitle,
} from '@/components/ui/sheet';
import { Entity, EntityPropertyType, SaveEntityRequest, SchemaUUID } from '@/lib/types/entity';
import { SchemaType } from '@/lib/types/common';
import { NoteEntry, extractNoteTitle, formatNoteTimestamp } from '@/lib/types/entity';
import { debounce } from '@/lib/util/debounce.util';
import { buildEntityUpdatePayload } from '@/components/feature-modules/entity/util/entity-payload.util';
import { useSaveEntityMutation } from '@/components/feature-modules/entity/hooks/mutation/instance/use-save-entity-mutation';
import { useQueryClient } from '@tanstack/react-query';
import { Button } from '@riven/ui/button';
import { Trash2 } from 'lucide-react';
import { Component, FC, ReactNode, useCallback, useEffect, useRef, useState } from 'react';
import { toast } from 'sonner';
import dynamic from 'next/dynamic';

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
  entity: Entity;
  attributeId: string;
  schema: SchemaUUID;
  workspaceId: string;
  entityTypeId: string;
  noteId: string;
  allNotes: NoteEntry[];
}

export const NoteDrawer: FC<NoteDrawerProps> = ({
  open,
  onClose,
  entity,
  attributeId,
  schema,
  workspaceId,
  entityTypeId,
  noteId,
  allNotes,
}) => {
  const queryClient = useQueryClient();
  const { mutate: saveEntity } = useSaveEntityMutation(workspaceId, entityTypeId);

  const currentNote = allNotes.find((n) => n.id === noteId);
  const [localTitle, setLocalTitle] = useState(currentNote?.title ?? 'Untitled');

  // Stable reference to allNotes for the debounced save
  const allNotesRef = useRef(allNotes);
  allNotesRef.current = allNotes;

  const debouncedSave = useRef(
    debounce((updatedNote: NoteEntry) => {
      const updatedNotes = allNotesRef.current.map((n) =>
        n.id === noteId ? updatedNote : n,
      );

      const request: SaveEntityRequest = buildEntityUpdatePayload(entity, attributeId, {
        payload: {
          type: EntityPropertyType.Attribute,
          value: updatedNotes,
          schemaType: SchemaType.Note,
        },
      });

      saveEntity(request);
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
    queryClient.invalidateQueries({
      queryKey: ['entities', workspaceId, entityTypeId, 'query'],
    });
    onClose();
  }, [debouncedSave, queryClient, workspaceId, entityTypeId, onClose]);

  const handleChange = useCallback(
    (blocks: object[]) => {
      if (!currentNote) return;
      const title = extractNoteTitle(blocks);
      setLocalTitle(title || 'Untitled');

      const updatedNote: NoteEntry = {
        ...currentNote,
        content: blocks,
        title,
        updatedAt: new Date().toISOString(),
      };
      debouncedSave(updatedNote);
    },
    [currentNote, debouncedSave],
  );

  const handleDelete = useCallback(() => {
    debouncedSave.cancel();

    const updatedNotes = allNotesRef.current.filter((n) => n.id !== noteId);
    const request: SaveEntityRequest = buildEntityUpdatePayload(entity, attributeId, {
      payload: {
        type: EntityPropertyType.Attribute,
        value: updatedNotes,
        schemaType: SchemaType.Note,
      },
    });

    saveEntity(request);
    toast.success('Note deleted');
    onClose();
  }, [debouncedSave, entity, attributeId, noteId, saveEntity, onClose]);

  if (!currentNote) {
    return null;
  }

  return (
    <Sheet open={open} onOpenChange={(isOpen) => !isOpen && handleClose()}>
      <SheetContent side="right" className="w-full sm:max-w-2xl">
        <SheetHeader className="flex-row items-center justify-between gap-2">
          <div className="min-w-0 flex-1">
            <SheetTitle className="truncate">{localTitle || 'Untitled'}</SheetTitle>
            <SheetDescription className="text-xs">
              Created {formatNoteTimestamp(currentNote.createdAt)}
              {currentNote.updatedAt !== currentNote.createdAt &&
                ` · Updated ${formatNoteTimestamp(currentNote.updatedAt)}`}
            </SheetDescription>
          </div>
          <Button
            variant="ghost"
            size="icon"
            className="size-8 text-destructive hover:bg-destructive/10"
            onClick={handleDelete}
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
                  {JSON.stringify(currentNote.content, null, 2)}
                </pre>
              </div>
            }
          >
            <BlockEditor
              key={noteId}
              initialContent={currentNote.content}
              onChange={handleChange}
            />
          </EditorErrorBoundary>
        </div>
      </SheetContent>
    </Sheet>
  );
};
