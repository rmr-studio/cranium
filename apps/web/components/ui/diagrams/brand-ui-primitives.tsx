import { cn } from '@/lib/utils';
import { Logo } from '@riven/ui/logo';

export function ShowcaseIconRail({
  icons,
  showWorkspace,
}: {
  icons: { icon: React.ReactNode; active?: boolean }[];
  showWorkspace?: boolean;
}) {
  return (
    <div className="flex h-full w-14 shrink-0 flex-col items-center bg-foreground dark:bg-secondary">
      <div className="flex h-12 w-full items-center justify-center border-b border-background/15">
        <Logo size={20} className="fill-background dark:fill-foreground" />
      </div>

      {showWorkspace && (
        <>
          <div className="pt-3 pb-2">
            <div className="flex size-8 items-center justify-center rounded-md bg-background/15 text-xs font-bold text-background">
              R
            </div>
          </div>
          <div className="mx-auto my-2 h-px w-6 bg-background/20" />
        </>
      )}

      <nav className={cn('flex flex-col items-center gap-1', !showWorkspace && 'pt-4')}>
        {icons.map((item, i) => (
          <div
            key={i}
            className={cn(
              'flex size-10 items-center justify-center rounded-md text-background/50 dark:text-foreground/50',
              item.active &&
                'bg-background/15 text-background dark:bg-foreground/20 dark:text-foreground',
            )}
          >
            {item.icon}
          </div>
        ))}
      </nav>
    </div>
  );
}

export function MockBreadcrumb({ items }: { items: string[] }) {
  return (
    <div className="flex items-center gap-1.5">
      {items.map((item, i) => (
        <span key={i} className="flex items-center gap-1.5">
          {i > 0 && <span className="text-xs text-muted-foreground/40">/</span>}
          <span
            className={cn(
              'font-display text-xs tracking-[0.05em] uppercase',
              i === items.length - 1 ? 'text-foreground' : 'text-muted-foreground/50',
            )}
          >
            {item}
          </span>
        </span>
      ))}
    </div>
  );
}

export function StatusDot({ color, label }: { color: string; label: string }) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span className="inline-block size-2.5 rounded-full" style={{ backgroundColor: color }} />
      <span className="text-sm text-foreground/80">{label}</span>
    </span>
  );
}

export function EntityChip({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <span className="inline-flex items-center gap-0.5 rounded border border-border/60 bg-muted/40 px-1 py-px leading-none whitespace-nowrap">
      {icon}
      <span className="text-[10px] text-foreground/70">{label}</span>
    </span>
  );
}

export function TableHeader({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <span className="flex items-center gap-1.5 text-xs text-muted-foreground">
      <span className="text-muted-foreground/50">{icon}</span>
      {label}
    </span>
  );
}

export function PlatformChip({ icon, label }: { icon: React.ReactNode; label: string }) {
  return (
    <span className="inline-flex items-center gap-1 rounded bg-muted px-1.5 py-px align-middle">
      {icon}
      <span className="text-foreground/70">{label}</span>
    </span>
  );
}

export function ShowcaseSubPanel({
  children,
  className,
}: {
  children: React.ReactNode;
  className?: string;
}) {
  return (
    <div
      className={cn(
        'flex h-full w-56 shrink-0 flex-col border-r border-border bg-background',
        className,
      )}
    >
      {children}
    </div>
  );
}

export function SymbolMark({
  size = 24,
  className,
}: {
  size?: number;
  className?: string;
}) {
  return (
    <svg
      xmlns="http://www.w3.org/2000/svg"
      viewBox="0 0 92.2 65"
      width={size}
      height={(size * 65) / 92.2}
      className={className}
      aria-hidden="true"
    >
      <path
        fill="currentColor"
        d="M66.5,0H52.4l25.7,65h14.1L66.5,0z M25.7,0L0,65h14.4l5.3-13.6h26.9L51.8,65h14.4L40.5,0C40.5,0,25.7,0,25.7,0z M24.3,39.3l8.8-22.8l8.8,22.8H24.3z"
      />
    </svg>
  );
}

export function FeatureTag({ children }: { children: React.ReactNode }) {
  return (
    <span
      className="inline-block rounded-lg border-2 border-transparent bg-background/90 px-4 py-1.5 font-display text-xs tracking-[0.08em] text-muted-foreground/70 uppercase shadow-md backdrop-blur-sm"
      style={{
        background: `linear-gradient(var(--background), var(--background)) padding-box, conic-gradient(from 120deg, var(--cta-g1), var(--cta-g2) 15%, transparent 25%, transparent 85%, var(--cta-g3) 95%, var(--cta-g1)) border-box`,
      }}
    >
      {children}
    </span>
  );
}
