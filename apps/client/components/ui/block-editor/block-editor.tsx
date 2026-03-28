'use client';

import { BlockNoteSchema, defaultInlineContentSpecs, PartialBlock } from '@blocknote/core';
import { useCreateBlockNote } from '@blocknote/react';
import { BlockNoteView } from '@blocknote/shadcn';
import '@blocknote/shadcn/style.css';
import { useTheme } from 'next-themes';
import { EntityMention } from '@/components/ui/block-editor/entity-mention';

const schema = BlockNoteSchema.create({
  inlineContentSpecs: {
    ...defaultInlineContentSpecs,
    entityMention: EntityMention,
  },
});

interface BlockEditorProps {
  initialContent?: PartialBlock[];
  onChange?: (blocks: PartialBlock[]) => void;
  uploadFile?: (file: File) => Promise<string>;
  editable?: boolean;
}

export function BlockEditor({
  initialContent,
  onChange,
  uploadFile,
  editable = true,
}: BlockEditorProps) {
  const { resolvedTheme } = useTheme();

  const editor = useCreateBlockNote({
    schema,
    initialContent: initialContent,
    uploadFile,
  });

  return (
    <BlockNoteView
      editor={editor}
      editable={editable}
      onChange={() => onChange?.(editor.document as PartialBlock[])}
      theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
    />
  );
}
