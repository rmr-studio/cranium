'use client';

import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { isResponseError } from '@/lib/util/error/error.util';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { EntityTypesOverview } from '../types/entity-types-overview';

const EntityTypesDashboard = () => {
  const { data: workspace, isPending, error, isLoadingAuth } = useWorkspace();
  const router = useRouter();

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

  if (isPending || isLoadingAuth) {
    return <div>Loading...</div>;
  }

  if (!workspace) return null;

  const trail: BreadCrumbTrail[] = [
    { label: 'Home', href: '/dashboard' },
    { label: 'Workspaces', href: '/dashboard/workspace' },
    {
      label: workspace.name || 'Workspace',
      href: `/dashboard/workspace/${workspace.id}`,
    },
    {
      label: 'Entities',
      href: `/dashboard/workspace/${workspace.id}/entity`,
    },
  ];

  return (
    <>
      {/* Header */}
      <header className="mb-8 flex items-center justify-between">
        <BreadCrumbGroup items={trail} />
      </header>
      <section>
        <EntityTypesOverview workspaceId={workspace.id} />
      </section>
    </>
  );
};

export default EntityTypesDashboard;
