'use client';

import Image from 'next/image';
import Link from 'next/link';
import { Check, Clock, Puzzle } from 'lucide-react';

import { Badge } from '@riven/ui/badge';
import { Card } from '@riven/ui/card';
import { cn } from '@/lib/util/utils';
import { IntegrationDefinitionModel } from '@/lib/types/integration';

interface IntegrationCardProps {
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

export function IntegrationCard({ integration, isConnected, workspaceId }: IntegrationCardProps) {
  const isComingSoon = !integration.nangoProviderKey;

  const content = (
    <Card
      className={cn(
        'group p-5 transition-shadow hover:shadow-md',
        isComingSoon && 'opacity-60',
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
              <Badge className="gap-1 bg-emerald-500/15 text-emerald-700 dark:text-emerald-400 border-transparent text-xs">
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
        <p className="mt-3 line-clamp-2 text-xs text-muted-foreground">
          {integration.description}
        </p>
      )}
    </Card>
  );

  if (isComingSoon) {
    return content;
  }

  return (
    <Link href={`/dashboard/workspace/${workspaceId}/integrations/${integration.slug}`}>
      {content}
    </Link>
  );
}
