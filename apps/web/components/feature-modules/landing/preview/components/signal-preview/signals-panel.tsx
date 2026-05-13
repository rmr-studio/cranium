import { cn } from '@/lib/utils';
import { ClassNameProps } from '@cranium/utils';
import { FC } from 'react';
import type { Signal } from './data';
import { SIGNALS } from './data';
import { Icon, SeverityDot, icons } from './primitives';

interface Props extends ClassNameProps {
  activeId?: string;
}

export const SignalsPanel: FC<Props> = ({ className, activeId }) => {
  const live = SIGNALS.filter((s) => s.state === 'live');
  const watch = SIGNALS.filter((s) => s.state === 'watch');
  const resolved = SIGNALS.filter((s) => s.state === 'resolved');

  return (
    <div className={cn('flex w-60 shrink-0 flex-col border-r border-border bg-sidebar', className)}>
      {/* workspace header */}
      <div className="flex items-center gap-2 border-b border-border px-3.5 py-3">
        <div className="min-w-0 flex-1">
          <div className="font-mono text-[9px] font-bold tracking-[0.06em] text-muted-foreground uppercase">
            Workspace
          </div>
          <div className="truncate font-display text-sm tracking-tight text-heading">
            Olive &amp; Orchard
          </div>
        </div>
        <button className="flex size-7 items-center justify-center rounded-md text-muted-foreground">
          <Icon size={14}>{icons.settings}</Icon>
        </button>
      </div>

      {/* search */}
      <div className="px-3 pt-2.5">
        <div className="flex items-center gap-2 rounded-md border border-border bg-background px-2.5 py-1.5 text-xs text-muted-foreground">
          <Icon size={12}>{icons.search}</Icon>
          <span className="flex-1">Filter signals…</span>
          <kbd className="rounded-sm border border-border bg-muted px-1 py-px font-mono text-[10px]">
            ⌘K
          </kbd>
        </div>
      </div>

      {/* primary nav */}
      <div className="px-2 pt-1.5">
        <NavRow icon="bell" label="All signals" count={SIGNALS.length} />
        <NavRow icon="bookmark" label="Pinned" count={2} />
        <NavRow icon="flow" label="Triggered actions" count={4} />
      </div>

      {/* sections */}
      <div className="flex-1 overflow-hidden px-2 pb-3">
        <SectionLabel label={`Live · ${live.length}`} open />
        {live.map((s) => (
          <SignalRow key={s.id} signal={s} active={s.id === activeId} />
        ))}

        <SectionLabel label={`Watching · ${watch.length}`} open />
        {watch.map((s) => (
          <SignalRow key={s.id} signal={s} active={s.id === activeId} />
        ))}

        <SectionLabel label={`Resolved · ${resolved.length}`} open={false} />
      </div>
    </div>
  );
};

function NavRow({
  icon,
  label,
  count,
}: {
  icon: keyof typeof icons;
  label: string;
  count?: number;
}) {
  return (
    <div className="mb-px flex items-center gap-2 rounded-md px-2.5 py-1.5 text-[13px] font-medium tracking-tight text-content">
      <span className="flex w-4 justify-center text-muted-foreground">
        <Icon size={13}>{icons[icon]}</Icon>
      </span>
      <span className="flex-1 truncate">{label}</span>
      {count != null && (
        <span className="font-mono text-[11px] text-muted-foreground tabular-nums">{count}</span>
      )}
    </div>
  );
}

function SectionLabel({ label, open }: { label: string; open: boolean }) {
  return (
    <div className="flex items-center gap-1.5 px-2.5 pt-3.5 pb-1.5 font-mono text-[10px] font-bold tracking-[0.08em] text-muted-foreground uppercase">
      <span
        className="flex transition-transform"
        style={{ transform: `rotate(${open ? 90 : 0}deg)` }}
      >
        <Icon size={9}>{icons.chevron}</Icon>
      </span>
      <span>{label}</span>
    </div>
  );
}

function SignalRow({ signal, active }: { signal: Signal; active: boolean }) {
  return (
    <div
      className={cn(
        'mb-0.5 flex items-start gap-2.5 rounded-md px-2.5 py-2 text-left',
        active ? 'bg-foreground' : '',
      )}
    >
      <span className="mt-1.5">
        <SeverityDot severity={signal.severity} pulse={signal.severity === 'high'} />
      </span>
      <span className="min-w-0 flex-1">
        <span
          className={cn(
            'block truncate text-[13px] tracking-tight',
            signal.unread ? 'font-semibold' : 'font-medium',
            active ? 'text-[oklch(0.97_0.008_92)]' : 'text-heading',
          )}
        >
          {signal.title}
        </span>
        <span
          className={cn(
            'mt-0.5 block truncate font-mono text-[10px]',
            active ? 'text-[oklch(0.7_0.005_92)]' : 'text-muted-foreground',
          )}
        >
          {signal.source} · {signal.time}
        </span>
      </span>
      {signal.unread && !active && (
        <span
          className="mt-1.5 size-1.5 shrink-0 rounded-full"
          style={{ background: 'oklch(0.7 0.12 348)' }}
        />
      )}
    </div>
  );
}
