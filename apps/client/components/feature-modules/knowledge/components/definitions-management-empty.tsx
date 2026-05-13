'use client';

import { Button } from '@cranium/ui/button';
import Link from 'next/link';

export function DefinitionsEmpty({ workspaceId }: { workspaceId: string }) {
  return (
    <div className="flex flex-col items-center gap-4 py-16 text-center">
      <h2 className="text-heading text-2xl font-semibold tracking-tight">
        Define Your Business Language
      </h2>
      <p className="text-muted-foreground max-w-md text-sm leading-relaxed">
        Business definitions create a shared vocabulary across your workspace.
        Define what terms like MRR, churn rate, or enterprise mean to your team.
      </p>
      <div className="flex gap-3">
        <Button asChild>
          <Link href={`/dashboard/workspace/${workspaceId}/definitions/new`}>
            Add Definition
          </Link>
        </Button>
      </div>
    </div>
  );
}
