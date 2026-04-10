import Link from 'next/link';
import { ArrowLeft } from 'lucide-react';
import { Skeleton } from '@/components/ui/skeleton';

export function IntegrationDetailSkeleton({ backHref }: { backHref: string }) {
  return (
    <div className="flex flex-col">
      <Link
        href={backHref}
        className="mb-4 inline-flex items-center gap-1.5 text-sm text-muted-foreground hover:text-foreground"
      >
        <ArrowLeft className="h-3.5 w-3.5" />
        Back to Integrations
      </Link>
      <Skeleton className="h-32 w-full rounded-lg" />
      <div className="-mt-8 flex items-end gap-4 px-4">
        <Skeleton className="h-16 w-16 rounded-lg" />
        <div className="flex flex-col gap-2 pb-1">
          <Skeleton className="h-7 w-48" />
          <Skeleton className="h-5 w-24" />
        </div>
      </div>
      <div className="mt-6 space-y-2 px-4">
        <Skeleton className="h-4 w-full max-w-2xl" />
        <Skeleton className="h-4 w-3/4 max-w-2xl" />
      </div>
      <div className="mt-8 border-t px-4 pt-6">
        <Skeleton className="h-6 w-32" />
        <Skeleton className="mt-3 h-10 w-28" />
      </div>
    </div>
  );
}
