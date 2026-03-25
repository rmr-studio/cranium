import { NavigationMenuLink } from '@/components/ui/navigation-menu';
import Link from 'next/link';
import type React from 'react';

interface DropdownListItemProps {
  icon: React.ComponentType<{ className?: string }>;
  label: string;
  description: string;
  href: string;
}

export function DropdownListItem({ icon: Icon, label, description, href }: DropdownListItemProps) {
  return (
    <li>
      <NavigationMenuLink asChild>
        <Link
          href={href}
          className="flex h-full w-auto grow flex-row items-center gap-3 rounded-sm p-3 transition-colors hover:bg-foreground/10"
        >
          <Icon className="size-6" />

          <div className="flex w-full flex-col gap-0.5 font-display">
            <span className="text-sm leading-tight font-medium tracking-tighter text-foreground">
              {label}
            </span>
            <span className="text-xs leading-snug tracking-tight text-muted-foreground">
              {description}
            </span>
          </div>
        </Link>
      </NavigationMenuLink>
    </li>
  );
}
