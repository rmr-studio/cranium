import { IconCell } from '@/components/ui/icon/icon-cell';
import { WorkspaceNote } from '@/lib/types';
import { IconColour, IconType } from '@/lib/types/common';
import { formatNoteTimestamp } from '@/lib/types/entity';
import Link from 'next/link';
import { FC } from 'react';

interface NoteRowProps {
  note: WorkspaceNote;
  workspaceId: string;
}

export const NoteRow: FC<NoteRowProps> = ({ note, workspaceId }) => {
  return (
    <Link
      href={`/dashboard/workspace/${workspaceId}/notes/${note.id}`}
      className="flex items-center gap-4 px-4 py-3 transition-colors hover:bg-muted/50"
    >
      {/* Note title */}
      <div className="min-w-0 flex-1">
        <p className="truncate text-sm font-medium">
          {note.title || 'Untitled'}
        </p>
      </div>

      {/* Entity badge */}
      <div className="flex items-center gap-1.5 text-xs text-muted-foreground">
        <IconCell
          readonly
          type={note.entityTypeIcon as IconType}
          colour={note.entityTypeColour as IconColour}
          className="size-3.5"
        />
        <span className="max-w-32 truncate">
          {note.entityDisplayName ?? 'Unknown'}
        </span>
      </div>

      {/* Timestamp */}
      <span className="shrink-0 text-xs tabular-nums text-muted-foreground">
        {formatNoteTimestamp(note.updatedAt ?? note.createdAt)}
      </span>
    </Link>
  );
};
