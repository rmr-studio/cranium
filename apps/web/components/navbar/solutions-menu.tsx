import { DropdownListItem } from '@/components/navbar/dropdown-list-item';
import {
  NavigationMenuContent,
  NavigationMenuItem,
  NavigationMenuTrigger,
} from '@/components/ui/navigation-menu';
import { Layers, Rocket, Target, TrendingUp, Users, Zap } from 'lucide-react';

const BUSINESS_TYPE_ITEMS = [
  {
    label: 'B2C SaaS',
    description: 'Elevate churn rates, boost retention, and grow your recurring revenue.',
    href: '/solutions/agencies',
    icon: Users,
  },
  {
    label: 'DTC & E-Commerce',
    description:
      'Elevate your ecommerce strategy and drive growth with powerful, data-dcranium insights.',
    href: '/solutions/ecommerce',
    icon: Rocket,
  },
];

const GOAL_ITEMS = [
  {
    label: 'Unify Your Data',
    description: 'Connect tools into one source of truth',
    href: '/solutions/unify-data',
    icon: Layers,
  },
  {
    label: 'Automate Workflows',
    description: 'Replace manual processes with actions',
    href: '/solutions/automate',
    icon: Zap,
  },
  {
    label: 'Grow Revenue',
    description: 'Track pipeline and close faster',
    href: '/solutions/grow-revenue',
    icon: TrendingUp,
  },
  {
    label: 'Reduce Churn',
    description: 'Spot at-risk accounts early',
    href: '/solutions/reduce-churn',
    icon: Target,
  },
];

export function SolutionsMenu() {
  return (
    <NavigationMenuItem>
      <NavigationMenuTrigger className="bg-transparent text-muted-foreground hover:text-foreground data-[state=open]:text-foreground">
        Solutions
      </NavigationMenuTrigger>
      <NavigationMenuContent>
        <div className="grid w-[540px] grid-cols-2 gap-4 p-4">
          <div className="flex flex-col gap-1">
            <h4 className="px-3 pt-1 pb-1 font-display text-xs tracking-widest text-muted-foreground uppercase">
              By Business Type
            </h4>
            <ul className="flex flex-col gap-0.5">
              {BUSINESS_TYPE_ITEMS.map((item) => (
                <DropdownListItem key={item.label} {...item} />
              ))}
            </ul>
          </div>
          <div className="flex flex-col gap-1">
            <h4 className="px-3 pt-1 pb-1 font-display text-xs tracking-widest text-muted-foreground uppercase">
              By Goal
            </h4>
            <ul className="flex flex-col gap-0.5">
              {GOAL_ITEMS.map((item) => (
                <DropdownListItem key={item.label} {...item} />
              ))}
            </ul>
          </div>
        </div>
      </NavigationMenuContent>
    </NavigationMenuItem>
  );
}
