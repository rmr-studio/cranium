'use client';

import { useMemo } from 'react';
import Link from 'next/link';
import Image from 'next/image';
import { useParams } from 'next/navigation';
import { ArrowLeft, Puzzle, Check, Clock } from 'lucide-react';
import { toast } from 'sonner';
import { Button } from '@riven/ui/button';
import { Badge } from '@riven/ui/badge';
import { IntegrationDetailSkeleton } from '@/components/feature-modules/integrations/components/integration-detail-skeleton';
import { useIntegrations } from '@/components/feature-modules/integrations/hooks/query/use-integrations';
import { useIntegrationStatus } from '@/components/feature-modules/integrations/hooks/query/use-integration-status';
import { useDisableIntegration } from '@/components/feature-modules/integrations/hooks/mutation/use-disable-integration';
import { ConnectionStatus } from '@/lib/types/integration';
import { IntegrationDisconnectDialog } from '@/components/feature-modules/integrations/components/integration-disconnect-dialog';

export function IntegrationDetail() {
  const { workspaceId, slug } = useParams<{ workspaceId: string; slug: string }>();
  const { data: integrations, isLoading: isLoadingIntegrations, isError: isIntegrationsError } = useIntegrations();
  const { data: connections, isLoading: isLoadingStatus, isError: isStatusError } = useIntegrationStatus(workspaceId);
  const disableMutation = useDisableIntegration(workspaceId);

  const integration = useMemo(
    () => integrations?.find((i) => i.slug === slug),
    [integrations, slug],
  );

  const connection = useMemo(
    () =>
      connections?.find(
        (c) => c.integrationId === integration?.id && c.status !== ConnectionStatus.Disconnected,
      ),
    [connections, integration?.id],
  );

  const isConnected = !!connection;
  const isLoading = isLoadingIntegrations || isLoadingStatus;
  const backHref = `/dashboard/workspace/${workspaceId}/integrations`;

  if (isLoading) {
    return <IntegrationDetailSkeleton backHref={backHref} />;
  }

  if (isIntegrationsError || isStatusError) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-24">
        <Puzzle className="h-12 w-12 text-muted-foreground/40" />
        <p className="text-lg font-medium text-foreground">Failed to load integration</p>
        <p className="text-sm text-muted-foreground">Something went wrong. Please try refreshing the page.</p>
        <Link href={backHref} className="text-sm text-muted-foreground hover:text-foreground">
          &larr; Back to Integrations
        </Link>
      </div>
    );
  }

  if (!integration) {
    return (
      <div className="flex flex-col items-center justify-center gap-4 py-24">
        <Puzzle className="h-12 w-12 text-muted-foreground/40" />
        <p className="text-lg font-medium text-foreground">Integration not found</p>
        <Link href={backHref} className="text-sm text-muted-foreground hover:text-foreground">
          &larr; Back to Integrations
        </Link>
      </div>
    );
  }

  const handleConnect = () => {
    toast.info(
      'Nango connect flow will be wired here. Provider key: ' + integration.nangoProviderKey,
    );
    // TODO: Wire nango.openConnectUI() when @nangohq/frontend is installed
  };

  const handleDisconnect = () => {
    disableMutation.mutate(integration.id);
  };

  const capabilityKeys = Object.keys(integration.capabilities ?? {});
  const hasNangoKey = !!integration.nangoProviderKey;

  return (
    <div className="flex flex-col">
      {/* Back link */}
      <Link
        href={backHref}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to Integrations
      </Link>

      {/* Cover gradient */}
      <div className="h-32 rounded-lg bg-gradient-to-r from-muted to-background" />

      {/* Header */}
      <div className="-mt-8 flex items-end gap-4 px-4">
        <div className="flex h-16 w-16 items-center justify-center rounded-lg border bg-card shadow-sm">
          {integration.iconUrl ? (
            <Image
              src={integration.iconUrl}
              alt={integration.name}
              width={40}
              height={40}
              className="rounded"
            />
          ) : (
            <Puzzle className="h-8 w-8 text-muted-foreground" />
          )}
        </div>
        <div className="flex flex-1 items-center gap-3 pb-1">
          <h1 className="text-2xl font-bold tracking-tight">{integration.name}</h1>
          <Badge variant="secondary">{integration.category}</Badge>
          {isConnected && (
            <Badge
              variant="outline"
              className="border-emerald-500/30 bg-emerald-500/10 text-emerald-600 dark:text-emerald-400"
            >
              <Check className="mr-1 h-3 w-3" />
              Connected
            </Badge>
          )}
        </div>
      </div>

      {/* Description */}
      {integration.description && (
        <p className="mt-6 max-w-2xl px-4 text-sm text-muted-foreground">
          {integration.description}
        </p>
      )}

      {/* Capabilities */}
      {capabilityKeys.length > 0 && (
        <div className="mt-8 px-4">
          <h2 className="text-lg font-semibold">Capabilities</h2>
          <ul className="mt-2 flex flex-wrap gap-2">
            {capabilityKeys.map((key) => (
              <li key={key}>
                <Badge variant="secondary" className="font-mono text-xs">
                  {key}
                </Badge>
              </li>
            ))}
          </ul>
        </div>
      )}

      {/* Connect / Disconnect */}
      <div className="mt-8 border-t px-4 pt-6">
        <h2 className="text-lg font-semibold">Connection</h2>
        <div className="mt-3">
          {isConnected ? (
            <IntegrationDisconnectDialog
              integrationName={integration.name}
              onConfirm={handleDisconnect}
              isPending={disableMutation.isPending}
            />
          ) : hasNangoKey ? (
            <Button onClick={handleConnect}>Connect</Button>
          ) : (
            <div className="flex items-center gap-2 text-sm text-muted-foreground">
              <Clock className="h-4 w-4" />
              <span>Coming Soon</span>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
