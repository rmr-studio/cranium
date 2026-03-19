'use client';

import { BlockNoteSchema, defaultInlineContentSpecs } from '@blocknote/core';
import { useCreateBlockNote } from '@blocknote/react';
import { BlockNoteView } from '@blocknote/shadcn';
import '@blocknote/shadcn/style.css';
import { useTheme } from 'next-themes';
import { EntityMention } from './entity-mention';

const schema = BlockNoteSchema.create({
  inlineContentSpecs: {
    ...defaultInlineContentSpecs,
    entityMention: EntityMention,
  },
});

interface BlockEditorProps {
  initialContent?: object[];
  onChange?: (blocks: object[]) => void;
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
    initialContent: initialContent as any,
    uploadFile,
  });

  return (
    <BlockNoteView
      editor={editor}
      editable={editable}
      onChange={() => onChange?.(editor.document as unknown as object[])}
      theme={resolvedTheme === 'dark' ? 'dark' : 'light'}
    />
  );
}
