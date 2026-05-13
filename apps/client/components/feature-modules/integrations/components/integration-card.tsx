'use client';

import { Check, Clock, Puzzle } from 'lucide-react';
import Image from 'next/image';

import { DefinitionDetailView } from '@/components/ui/sidebar/types/side-panel.types';
import { IntegrationDefinitionModel } from '@/lib/types/integration';
import { cn } from '@/lib/util/utils';
import { Badge } from '@cranium/ui/badge';
import { Card } from '@cranium/ui/card';

interface IntegrationCardProps {
  onClick: (view: DefinitionDetailView) => void;
  integration: IntegrationDefinitionModel;
  isConnected: boolean;
  workspaceId: string;
}

const CATEGORY_LABELS: Record<string, string> = {
  CRM: 'CRM',
  PAYMENTS: 'Payments',
  SUPPORT: 'Support',
  COMMUNICATION: 'Communication',
  PRODUCT_ANALYTICS: 'Analytics',
};

export function IntegrationCard({
  integration,
  isConnected,
  workspaceId,
  onClick,
}: IntegrationCardProps) {
  const isComingSoon = !integration.nangoProviderKey;

  const handleSelect = () => {
    if (isComingSoon) return;

    const view: DefinitionDetailView = {
      type: 'definition-detail',
      integration,
      workspaceId,
    };
    onClick(view);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLDivElement>) => {
    if (isComingSoon) return;
    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      handleSelect();
    }
  };

  return (
    <Card
      role="button"
      tabIndex={isComingSoon ? -1 : 0}
      aria-disabled={isComingSoon}
      onClick={handleSelect}
      onKeyDown={handleKeyDown}
      className={cn(
        'group cursor-pointer p-5 outline-none transition-shadow hover:shadow-md focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2',
        isComingSoon && 'cursor-not-allowed opacity-60',
      )}
    >
      <div className="flex items-start gap-3">
        <div className="flex size-10 shrink-0 items-center justify-center rounded-lg border bg-muted">
          {integration.iconUrl ? (
            <Image
              src={integration.iconUrl}
              alt={integration.name}
              width={24}
              height={24}
              className="size-6"
            />
          ) : (
            <Puzzle className="size-5 text-muted-foreground" />
          )}
        </div>
        <div className="min-w-0 flex-1">
          <div className="flex items-center gap-2">
            <span className="truncate text-sm font-semibold">{integration.name}</span>
          </div>
          <div className="mt-1 flex flex-wrap items-center gap-1.5">
            <Badge variant="secondary" className="text-xs">
              {CATEGORY_LABELS[integration.category] ?? integration.category}
            </Badge>
            {isConnected && (
              <Badge className="gap-1 border-transparent bg-emerald-500/15 text-xs text-emerald-700 dark:text-emerald-400">
                <Check className="size-3" />
                Connected
              </Badge>
            )}
            {isComingSoon && (
              <Badge variant="outline" className="gap-1 text-xs text-muted-foreground">
                <Clock className="size-3" />
                Coming soon
              </Badge>
            )}
          </div>
        </div>
      </div>
      {integration.description && (
        <p className="mt-3 line-clamp-2 text-xs text-muted-foreground">{integration.description}</p>
      )}
    </Card>
  );
}
