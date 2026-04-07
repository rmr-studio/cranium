import { Skeleton } from '@/components/ui/skeleton';

export function PanelSkeleton() {
  return (
    <div className="flex flex-col gap-3 p-4">
      <Skeleton className="h-5 w-2/3 rounded" />
      <Skeleton className="h-4 w-full rounded" />
      <Skeleton className="h-4 w-5/6 rounded" />
      <Skeleton className="h-32 w-full rounded" />
      <Skeleton className="h-4 w-3/4 rounded" />
    </div>
  );
}
