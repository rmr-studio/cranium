import { cn } from '@/lib/utils';
import { Logo } from '@cranium/ui/logo';
import { ClassNameProps } from '@cranium/utils';
import { FC } from 'react';
import { RAIL_ITEMS } from './data';
import { Icon, icons } from './primitives';

interface Props extends ClassNameProps {
  activeId?: string;
}

export const IconRail: FC<Props> = ({ className, activeId }) => {
  return (
    <div
      className={cn('flex w-14 shrink-0 flex-col items-center gap-1 bg-foreground py-3', className)}
    >
      <div className="mb-3">
        <Logo size={22} />
      </div>
      {RAIL_ITEMS.map((item) => {
        const active = item.id === activeId;
        return (
          <span
            key={item.id}
            className={cn(
              'flex size-9 items-center justify-center rounded-md',
              active ? 'bg-white/15 text-[oklch(0.98_0_0)]' : 'text-[oklch(0.7_0_0)]',
            )}
          >
            <Icon size={18}>{icons[item.icon]}</Icon>
          </span>
        );
      })}
      <span className="flex-1" />
      <span className="flex size-9 items-center justify-center rounded-md text-[oklch(0.7_0_0)]">
        <Icon size={18}>{icons.settings}</Icon>
      </span>

      <span
        className="mt-1 flex size-7 items-center justify-center rounded-full font-mono text-[10px] font-bold tracking-tight text-black"
        style={{ background: 'oklch(0.7 0.12 348)' }}
      >
        RM
      </span>
    </div>
  );
};
