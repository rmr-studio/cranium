'use client';

import { useParams } from 'next/navigation';
import { FC } from 'react';
import { DefinitionEditorForm } from './definition-editor-form';

export const DefinitionEditor: FC = () => {
  const { workspaceId, id } = useParams<{ workspaceId: string; id?: string }>();
  return <DefinitionEditorForm workspaceId={workspaceId} definitionId={id} />;
};
