'use client';

import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { cn } from '@cranium/utils';
import { CalendarHeart, TrendingUpDown } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

export function BillingPanel() {
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  const pathname = usePathname();

  const items = [
    {
      label: 'Usage',
      icon: TrendingUpDown,
      href: `/dashboard/workspace/${selectedWorkspaceId}/usage`,
    },
    {
      label: 'Subscription',
      icon: CalendarHeart,
      href: `/dashboard/workspace/${selectedWorkspaceId}/subscriptions`,
    },
  ];

  return (
    <div className="flex flex-col gap-1">
      {items.map((item) => {
        const isActive = pathname.startsWith(item.href);
        return (
          <Link
            key={item.label}
            href={item.href}
            className={cn(
              'flex items-center gap-2 rounded-md px-3 py-2 text-sm text-sidebar-foreground/70 transition-colors hover:bg-sidebar-accent hover:text-sidebar-foreground',
              isActive && 'bg-sidebar-accent text-sidebar-foreground',
            )}
          >
            <item.icon className="size-4" />
            {item.label}
          </Link>
        );
      })}
    </div>
  );
}
