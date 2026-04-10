'use client';

import { Skeleton } from '@/components/ui/skeleton';

export function EditorSkeleton() {
  return (
    <div className="mx-auto max-w-2xl px-8 py-8">
      <Skeleton className="mb-8 h-5 w-24" />
      <div className="space-y-6">
        <Skeleton className="h-10 w-full" />
        <Skeleton className="h-32 w-full" />
        <Skeleton className="h-10 w-48" />
      </div>
    </div>
  );
}
