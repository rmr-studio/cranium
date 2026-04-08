'use client';

import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { isResponseError } from '@/lib/util/error/error.util';
import { useParams, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { EntityTypeConfigurationProvider } from '../../context/configuration-provider';
import { useEntityTypeByKey } from '../../hooks/query/type/use-entity-types';
import { EntityTypeOverview } from '../types/entity-type';

export const EntityTypeOverviewDashboard = () => {
  const { data: workspace } = useWorkspace();
  const params = useParams<{ workspaceId: string; key: string }>();
  const { workspaceId, key } = params;
  const router = useRouter();
  const {
    data: entityType,
    isPending,
    error,
    isLoadingAuth,
  } = useEntityTypeByKey(key, workspaceId);

  useEffect(() => {
    // Query has finished, workspace has not been found. Redirect back to workspace view with associated error
    if (!isPending && !entityType) {
      if (!error || !isResponseError(error)) {
        router.push('/dashboard/workspace/');
        return;
      }

      // Query has returned an ID we can use to route to a valid error message
      const responseError = error;
      router.push(`/dashboard/workspace/${workspaceId}/entity?error=${responseError.error}`);
    }
  }, [isPending, isLoadingAuth, entityType, error, router]);

  if (isPending) {
    return <div>Loading entity type...</div>;
  }

  if (!entityType) return null;

  const trail: BreadCrumbTrail[] = [
    { label: 'Home', href: '/dashboard' },
    { label: 'Workspaces', href: '/dashboard/workspace', truncate: true },
    {
      label: workspace?.name || 'Workspace',
      href: `/dashboard/workspace/${workspaceId}`,
      truncate: true,
    },
    {
      label: 'Entities',
      href: `/dashboard/workspace/${workspaceId}/entity`,
    },
    {
      label: entityType.name.plural,
      href: `/dashboard/workspace/${workspaceId}/entity/${entityType.key}`,
    },
    {
      label: 'Settings',
      href: `/dashboard/workspace/${workspaceId}/entity/${entityType.key}/settings`,
    },
  ];

  return (
    <>
      <header className="mb-8 flex items-center justify-between">
        <BreadCrumbGroup items={trail} />
      </header>
      <section>
        <EntityTypeConfigurationProvider workspaceId={workspaceId} entityType={entityType}>
          <EntityTypeOverview workspaceId={workspaceId} entityType={entityType} />
        </EntityTypeConfigurationProvider>
      </section>
    </>
  );
};
