import { Button } from '@cranium/ui/button';
import { FormField } from '@/components/ui/form';
import { IconSelector } from '@/components/ui/icon/icon-selector';
import type { ChildNodeProps } from '@cranium/utils';
import { cn } from '@/lib/util/utils';
import { Cog, File } from 'lucide-react';
import Link from 'next/link';
import { usePathname } from 'next/navigation';
import { FC } from 'react';
import { UseFormReturn, useWatch } from 'react-hook-form';
import {
  useConfigCurrentType,
  useConfigForm,
  type EntityTypeFormValues,
} from '../../context/configuration-provider';

interface Props extends ChildNodeProps {}

export const EntityTypeHeader: FC<Props> = ({ children }) => {
  const type = useConfigCurrentType();
  const form: UseFormReturn<EntityTypeFormValues> = useConfigForm();
  const pathname = usePathname();

  const name = useWatch({
    control: form.control,
    name: 'pluralName',
  });

  const { key } = type;
  const basePath = `/dashboard/workspace/${type.workspaceId}/entity/${key}`;
  const isSettings = pathname.endsWith('/settings');

  const navItems = [
    { label: 'Entities', href: basePath, icon: File, active: !isSettings },
    { label: 'Settings', href: `${basePath}/settings`, icon: Cog, active: isSettings },
  ];

  return (
    <div className="space-y-2">
      <div className="flex items-center gap-3">
        <FormField
          control={form.control}
          name="icon"
          render={({ field }) => (
            <IconSelector
              onSelect={field.onChange}
              icon={field.value}
              className="size-10 shrink-0 rounded-lg bg-accent/10"
              displayIconClassName="size-6"
            />
          )}
        />
        <div className="min-w-0">
          <h1 className="text-xl leading-tight font-semibold">{name}</h1>
          {children}
        </div>
      </div>
      <nav className="flex items-center gap-1">
        {navItems.map((item) => (
          <Link key={item.label} href={item.href}>
            <Button
              variant="ghost"
              size="xs"
              className={cn(
                'h-6 gap-1 px-2 text-xs font-medium',
                item.active
                  ? 'bg-accent text-accent-foreground'
                  : 'text-muted-foreground hover:bg-accent/50 hover:text-foreground',
              )}
            >
              <item.icon className="size-3.5" />
              {item.label}
            </Button>
          </Link>
        ))}
      </nav>
    </div>
  );
};
