'use client';

import { cn } from '@riven/utils';
import { FC } from 'react';

import type { AgentScenario } from '../data/use-case-types';

interface Props {
  items: AgentScenario[];
  activeId: string;
  onSelect: (id: string) => void;
  className?: string;
}

export const UseCaseTabs: FC<Props> = ({ items, activeId, onSelect, className }) => (
  <div
    className={cn(
      '-mx-4 flex gap-3 overflow-x-auto px-4 pb-2 md:mx-0 md:flex-wrap md:overflow-visible md:px-0 md:pb-0',
      className,
    )}
  >
    {items.map((uc) => {
      const active = uc.id === activeId;
      return (
        <button
          key={uc.id}
          type="button"
          onClick={() => onSelect(uc.id)}
          aria-pressed={active}
          className={cn(
            'group flex min-w-[260px] shrink-0 items-center gap-3 rounded-xl border px-4 py-3 text-left transition md:flex-1',
            active
              ? 'border-border bg-card shadow-sm'
              : 'border-border/40 bg-transparent hover:border-border hover:bg-card/50',
          )}
        >
          <span
            className={cn(
              'grid size-9 shrink-0 place-items-center rounded-lg border transition',
              active
                ? 'border-border bg-muted/50 text-foreground'
                : 'border-border/40 bg-muted/20 text-muted-foreground group-hover:text-foreground',
            )}
          >
            {uc.tabIcon}
          </span>

          <span className="flex min-w-0 flex-col">
            <span className="truncate text-sm font-medium text-foreground">{uc.tabTitle}</span>
            <span className="truncate text-xs text-muted-foreground">{uc.tabSubtitle}</span>
          </span>
        </button>
      );
    })}
  </div>
);
