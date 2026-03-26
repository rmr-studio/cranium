'use client';

import type { SaveWorkspaceRequest } from '@/lib/types/workspace';

import { useAuth } from '@/components/provider/auth-context';
import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { useRouter } from 'next/navigation';
import { useState } from 'react';
import { toast } from 'sonner';
import { useWorkspace } from '../hooks/query/use-workspace';

import { useSaveWorkspaceMutation } from '../hooks/mutation/use-save-workspace-mutation';
import { WorkspaceForm, WorkspaceFormDetails } from './form/workspace-form';

const EditWorkspace = () => {
  const { session } = useAuth();
  const { data: workspace } = useWorkspace();

  const router = useRouter();
  const [uploadedAvatar, setUploadedAvatar] = useState<Blob | null>(null);
  const { mutateAsync: saveWorkspace } = useSaveWorkspaceMutation({
    onSuccess() {
      router.push('/dashboard/workspace');
    },
  });

  const handleSubmission = async (values: WorkspaceFormDetails) => {
    if (!session) {
      toast.error('No active session found');
      return;
    }

    if (!workspace) {
      toast.error('Workspace not found');
      return;
    }

    const request: SaveWorkspaceRequest = {
      ...workspace,
      ...values,
      removeAvatar: false,
    };

    // Create the workspace
    await saveWorkspace({ workspace: request, avatar: uploadedAvatar });
  };

  const trail: BreadCrumbTrail[] = [
    { label: 'Home', href: '/dashboard' },
    { label: 'Workspaces', href: '/dashboard/workspace', truncate: true },
    {
      label: workspace?.name || 'Workspace',
      href: `/dashboard/workspace/${workspace?.id}/clients`,
    },
    { label: 'Edit', href: '#', active: true },
  ];

  if (!session || !workspace) return null;
  return (
    <WorkspaceForm
      className="m-8"
      onSubmit={handleSubmission}
      workspace={workspace}
      setUploadedAvatar={setUploadedAvatar}
      renderHeader={() => (
        <>
          <BreadCrumbGroup items={trail} className="mb-4" />
          <h1 className="mb-2 text-xl font-bold text-primary">Manage {workspace?.name}</h1>
          <p className="text-sm text-muted-foreground">
            Set up your workspace in just a few steps. This will help manage and store all important
            information you need when managing and invoicing your clients.
          </p>
        </>
      )}
    />
  );
};

export default EditWorkspace;
