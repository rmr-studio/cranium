'use client';

import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { isResponseError } from '@/lib/util/error/error.util';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { useWorkspace } from '../hooks/query/use-workspace';
import { useCurrentWorkspace } from '../provider/workspace-provider';

export const WorkspaceDashboard = () => {
  const { data: workspace, isPending, error, isLoadingAuth } = useWorkspace();

  const router = useRouter();

  const { setSelectedWorkspace, selectedWorkspaceId } = useCurrentWorkspace();

  useEffect(() => {
    // Query has finished, workspace has not been found. Redirect back to workspace view with associated error
    if (!isPending && !isLoadingAuth && !workspace) {
      if (!error || !isResponseError(error)) {
        router.push('/dashboard/workspace/');
        return;
      }

      // Query has returned an ID we can use to route to a valid error message
      const responseError = error;
      router.push(`/dashboard/workspace?error=${responseError.error}`);
    }
  }, [isPending, isLoadingAuth, workspace, error, router]);

  useEffect(() => {
    if (!workspace || !setSelectedWorkspace) return;
    if (selectedWorkspaceId === workspace.id) return;
    setSelectedWorkspace(workspace); // Pass the full Workspace object
  }, [workspace, selectedWorkspaceId, setSelectedWorkspace]);

  if (isPending || isLoadingAuth) {
    return <div>Loading...</div>;
  }

  if (!workspace) return;

  const trail: BreadCrumbTrail[] = [
    { label: 'Home', href: '/dashboard' },
    { label: 'Workspaces', href: '/dashboard/workspace' },
    {
      label: workspace.name || 'Workspace',
      href: `/dashboard/workspace/${workspace.id}`,
    },
  ];

  return (
    <>
      {/* Header */}
      <div className="mb-8 flex items-center justify-between">
        <BreadCrumbGroup items={trail} />
      </div>
    </>
  );
};
