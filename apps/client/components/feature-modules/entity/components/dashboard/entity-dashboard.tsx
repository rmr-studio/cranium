'use client';

import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { BreadCrumbGroup, BreadCrumbTrail } from '@/components/ui/breadcrumb-group';
import { isResponseError } from '@/lib/util/error/error.util';
import { TooltipProvider } from '@cranium/ui/tooltip';
import { useParams, useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { EntityTypeConfigurationProvider } from '../../context/configuration-provider';
import { EntityDraftProvider } from '../../context/entity-provider';
import { useEntityTypeByKey } from '../../hooks/query/type/use-entity-types';
import { EntityDataTable } from '../tables/entity-data-table';

export const EntityDashboard = () => {
  const { data: workspace } = useWorkspace();
  const { workspaceId, key: typeKey } = useParams<{ workspaceId: string; key: string }>();
  const router = useRouter();
  const {
    data: entityType,
    isPending: isPendingEntityType,
    error: entityTypeError,
    isLoadingAuth,
  } = useEntityTypeByKey(typeKey, workspaceId);

  useEffect(() => {
    // Query has finished, workspace has not been found. Redirect back to workspace view with associated error
    if (!isPendingEntityType && !entityType) {
      if (!entityTypeError || !isResponseError(entityTypeError)) {
        router.push('/dashboard/workspace/');
        return;
      }

      // Query has returned an ID we can use to route to a valid error message
      const responseError = entityTypeError;
      router.push(`/dashboard/workspace/${workspaceId}/entity?error=${responseError.error}`);
    }
  }, [isPendingEntityType, isLoadingAuth, entityType, entityTypeError, router]);

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
  ];

  return (
    <div className="relative overflow-hidden">
      <header className="mb-8 flex items-center justify-between">
        <BreadCrumbGroup items={trail} />
      </header>
      <section className="flex w-full min-w-0">
        <TooltipProvider>
          <EntityTypeConfigurationProvider workspaceId={workspaceId} entityType={entityType}>
            <EntityDraftProvider workspaceId={workspaceId} entityType={entityType}>
              <EntityDataTable entityType={entityType} workspaceId={workspaceId} />
            </EntityDraftProvider>
          </EntityTypeConfigurationProvider>
        </TooltipProvider>
      </section>
    </div>
  );
};
