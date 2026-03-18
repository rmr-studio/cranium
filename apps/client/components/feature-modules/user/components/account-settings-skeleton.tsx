import { Skeleton } from '@/components/ui/skeleton';

export function AccountSettingsSkeleton() {
  return (
    <div className="mx-auto w-full max-w-2xl space-y-10 px-6 py-10">
      <div className="space-y-6">
        <div>
          <Skeleton className="mb-2 h-7 w-32" />
          <Skeleton className="h-4 w-48" />
        </div>
        <Skeleton className="h-12 w-full rounded-lg" />
        <div className="flex items-center gap-4">
          <Skeleton className="size-18 rounded-full" />
          <div className="space-y-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-8 w-20 rounded-md" />
          </div>
        </div>
        <div className="grid grid-cols-2 gap-4">
          <Skeleton className="h-16" />
          <Skeleton className="h-16" />
        </div>
        <Skeleton className="h-16" />
      </div>
    </div>
  );
}
