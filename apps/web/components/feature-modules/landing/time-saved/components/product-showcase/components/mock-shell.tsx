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

// Map of entity names that have scenarios (clickable in sidebar)
const SCENARIO_ENTITIES = new Set(['Customers', 'Support Tickets', 'Subscriptions']);

const ENTITY_NAV_ICONS = [
  { icon: <Building2 className="size-5" />, active: false },
  { icon: <SquareDashedMousePointer className="size-5" />, active: true },
  { icon: <TrendingUpDown className="size-5" />, active: false },
  { icon: <CogIcon className="size-5" />, active: false },
];

export function MockIconRail() {
  return <ShowcaseIconRail icons={ENTITY_NAV_ICONS} showWorkspace />;
}

export function MockSubPanel({
  activeEntity,
  onEntityClick,
}: {
  activeEntity: string;
  onEntityClick?: (name: string) => void;
}) {
  const entities = [
    { icon: <Users className="size-4 text-blue-500" />, name: 'Customers' },
    { icon: <MessageSquare className="size-4 text-teal-500" />, name: 'Communications' },
    { icon: <Ticket className="size-4 text-orange-500" />, name: 'Support Tickets' },
    { icon: <Repeat className="size-4 text-green-500" />, name: 'Subscriptions' },
    { icon: <Activity className="size-4 text-teal-500" />, name: 'Feature Usage' },
    { icon: <Megaphone className="size-4 text-purple-500" />, name: 'Acquisition Sources' },
    { icon: <CreditCard className="size-4 text-green-500" />, name: 'Billing Events' },
    { icon: <UserMinus className="size-4 text-red-500" />, name: 'Churn Events' },
  ];

  return (
    <ShowcaseSubPanel>
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
          const isClickable = SCENARIO_ENTITIES.has(entity.name) && !isActive;

          return (
            <div key={entity.name} className="relative">
              {/* Shimmer border on clickable (non-active) scenario items */}
              {isClickable && (
                <div
                  className="absolute -inset-px animate-[border-rotate_4s_linear_infinite] rounded-lg opacity-50"
                  style={{
                    background: `conic-gradient(from var(--border-angle), var(--cta-g1), var(--cta-g2) 15%, transparent 25%, transparent 85%, var(--cta-g3) 95%, var(--cta-g1))`,
                    mask: 'linear-gradient(#000 0 0) content-box, linear-gradient(#000 0 0)',
                    maskComposite: 'exclude',
                    padding: '1px',
                  }}
                />
              )}
              <button
                type="button"
                onClick={isClickable ? () => onEntityClick?.(entity.name) : undefined}
                className={cn(
                  'relative flex w-full items-center gap-2 rounded-md px-3 py-2 text-left text-sm transition-colors',
                  isActive
                    ? 'bg-accent font-medium text-foreground'
                    : isClickable
                      ? 'cursor-pointer bg-background/80 text-muted-foreground/80 hover:text-foreground'
                      : 'cursor-default text-muted-foreground/40',
                )}
              >
                {entity.icon}
                <span>{entity.name}</span>
              </button>
            </div>
          );
        })}
      </div>
    </ShowcaseSubPanel>
  );
}
