import { cn } from '@/lib/utils';
import {
  Activity,
  Building2,
  CogIcon,
  CreditCard,
  Megaphone,
  MessageSquare,
  Repeat,
  Search,
  SquareDashedMousePointer,
  Ticket,
  TrendingUpDown,
  UserMinus,
  Users,
} from 'lucide-react';

import { ShowcaseIconRail, ShowcaseSubPanel } from '@/components/ui/diagrams/brand-ui-primitives';
import { ClassNameProps } from '@cranium/utils';
import { FC } from 'react';

const ENTITY_NAV_ICONS = [
  { icon: <Building2 className="size-5" />, active: false },
  { icon: <SquareDashedMousePointer className="size-5" />, active: true },
  { icon: <TrendingUpDown className="size-5" />, active: false },
  { icon: <CogIcon className="size-5" />, active: false },
];

export function MockIconRail() {
  return <ShowcaseIconRail icons={ENTITY_NAV_ICONS} showWorkspace />;
}

interface SubpanelProps extends ClassNameProps {
  activeEntity: string;
}

export const MockSubPanel: FC<SubpanelProps> = ({ activeEntity, className }) => {
  const entities = [
    { icon: <Users className="size-4" />, name: 'Customers' },
    { icon: <MessageSquare className="size-4" />, name: 'Communications' },
    { icon: <Ticket className="size-4" />, name: 'Support Tickets' },
    { icon: <Repeat className="size-4" />, name: 'Subscriptions' },
    { icon: <Activity className="size-4" />, name: 'Feature Usage' },
    { icon: <Megaphone className="size-4" />, name: 'Acquisition Sources' },
    { icon: <CreditCard className="size-4" />, name: 'Billing Events' },
    { icon: <UserMinus className="size-4" />, name: 'Churn Events' },
  ];

  return (
    <ShowcaseSubPanel className={className}>
      {/* Header */}
      <div className="flex h-12 shrink-0 items-center border-b border-border px-4">
        <span className="text-sm font-semibold text-foreground">Entities</span>
      </div>

      {/* Search */}
      <div className="px-3 pt-2.5 pb-2">
        <div className="flex items-center gap-1.5 rounded-md border border-border bg-muted/30 px-2.5 py-1.5">
          <Search className="size-3.5 text-muted-foreground/50" />
          <span className="text-xs text-muted-foreground/40">Search records...</span>
        </div>
      </div>

      {/* Entity list */}
      <div className="flex flex-col gap-0.5 px-2.5">
        {entities.map((entity) => {
          const isActive = entity.name === activeEntity;

          return (
            <div
              key={entity.name}
              className={cn(
                'flex items-center gap-2 rounded-md px-3 py-2 text-sm',
                isActive ? 'bg-accent font-medium text-foreground' : 'text-muted-foreground/40',
              )}
            >
              {entity.icon}
              <span>{entity.name}</span>
            </div>
          );
        })}
      </div>
    </ShowcaseSubPanel>
  );
};
