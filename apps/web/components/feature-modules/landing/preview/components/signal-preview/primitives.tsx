import { BrandIcons, type Integration } from '@/components/ui/diagrams/brand-icons';
import { cn } from '@/lib/utils';
import type { ReactNode, SVGProps } from 'react';

export const SEVERITY_COLOR: Record<string, string> = {
  high: 'oklch(0.62 0.22 27)',
  med: 'oklch(0.75 0.15 75)',
  low: 'oklch(0.6 0.1 263)',
  info: 'oklch(0.55 0.005 92)',
};

export const SOURCE_COLOR: Record<string, string> = {
  Shopify: '#5E8E3E',
  PostHog: '#1D4AFF',
  Klaviyo: '#000000',
  TikTok: '#FF0050',
  Reddit: '#FF4500',
  Zendesk: '#03363D',
  Stripe: '#635BFF',
  Meta: '#0866FF',
};

const AVATAR_PALETTE = [
  'oklch(0.7 0.12 348)',
  'oklch(0.61 0.09 263)',
  'oklch(0.55 0.13 145)',
  'oklch(0.7 0.13 75)',
  'oklch(0.55 0.15 27)',
  'oklch(0.5 0.12 277)',
];

function hashStr(s: string) {
  let h = 0;
  for (let i = 0; i < s.length; i++) h = (h + s.charCodeAt(i)) | 0;
  return Math.abs(h);
}

export function UserAvatar({
  name,
  size = 24,
  square = false,
  className,
}: {
  name: string;
  size?: number;
  square?: boolean;
  className?: string;
}) {
  const initials = name
    .split(/\s+/)
    .map((s) => s[0])
    .slice(0, 2)
    .join('')
    .toUpperCase();
  const bg = AVATAR_PALETTE[hashStr(name) % AVATAR_PALETTE.length];
  return (
    <span
      className={cn(
        'inline-flex shrink-0 items-center justify-center font-semibold tracking-tight text-white',
        className,
      )}
      style={{
        width: size,
        height: size,
        borderRadius: square ? Math.max(3, size * 0.18) : 9999,
        background: bg,
        fontSize: Math.max(8, size * 0.42),
      }}
    >
      {initials}
    </span>
  );
}

export function EntityChip({ children, hue = 70 }: { children: ReactNode; hue?: number }) {
  return (
    <span className="inline-flex items-center gap-1.5 rounded-sm border border-border bg-muted px-1.5 py-px text-[12px] font-medium tracking-tight text-heading">
      <span className="size-1.5 rounded-[1px]" style={{ background: `oklch(0.85 0.05 ${hue})` }} />
      {children}
    </span>
  );
}

export function ChatLink({ children }: { children: ReactNode }) {
  return (
    <span
      className="cursor-pointer underline underline-offset-2"
      style={{ color: 'oklch(0.5 0.18 263)' }}
    >
      {children}
    </span>
  );
}

export function SystemLabel({
  children,
  className,
  tone = 'muted',
}: {
  children: ReactNode;
  className?: string;
  tone?: 'muted' | 'heading' | 'destructive';
}) {
  const color =
    tone === 'destructive'
      ? 'text-[oklch(0.45_0.18_27)]'
      : tone === 'heading'
        ? 'text-heading'
        : 'text-muted-foreground';
  return (
    <span
      className={cn(
        'font-mono text-[10px] font-bold tracking-[0.06em] uppercase',
        color,
        className,
      )}
    >
      {children}
    </span>
  );
}

export function SeverityDot({
  severity,
  size = 8,
  pulse = false,
}: {
  severity: keyof typeof SEVERITY_COLOR;
  size?: number;
  pulse?: boolean;
}) {
  const c = SEVERITY_COLOR[severity];
  return (
    <span
      className="inline-block shrink-0 rounded-full"
      style={{
        width: size,
        height: size,
        background: c,
        boxShadow: pulse ? `0 0 0 3px ${c.replace(')', ' / 0.18)')}` : undefined,
      }}
    />
  );
}

export function AppGlyph({ app, size = 18 }: { app: string; size?: number }) {
  const key = app.split(/[\s·]+/)[0];
  const BrandIcon = BrandIcons[key as Integration];
  if (BrandIcon) {
    return (
      <span
        className="inline-flex shrink-0 items-center justify-center"
        style={{ width: size, height: size }}
      >
        <BrandIcon size={size} />
      </span>
    );
  }
  const bg = SOURCE_COLOR[key] ?? 'var(--muted-foreground)';
  return (
    <span
      className="inline-flex shrink-0 items-center justify-center font-mono font-bold text-white"
      style={{
        width: size,
        height: size,
        background: bg,
        borderRadius: Math.max(3, size * 0.18),
        fontSize: Math.max(8, size * 0.5),
      }}
    >
      {key[0]}
    </span>
  );
}

type SvgChildren = ReactNode;
export function Icon({
  children,
  size = 14,
  className,
  strokeWidth = 1.75,
  ...rest
}: { children: SvgChildren; size?: number; strokeWidth?: number } & SVGProps<SVGSVGElement>) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth={strokeWidth}
      strokeLinecap="round"
      strokeLinejoin="round"
      className={className}
      {...rest}
    >
      {children}
    </svg>
  );
}

export const icons = {
  grid: (
    <>
      <rect x="3" y="3" width="7" height="7" />
      <rect x="14" y="3" width="7" height="7" />
      <rect x="14" y="14" width="7" height="7" />
      <rect x="3" y="14" width="7" height="7" />
    </>
  ),
  table: (
    <>
      <path d="M3 3h18v18H3z" />
      <path d="M3 9h18M9 3v18" />
    </>
  ),
  flow: (
    <>
      <polyline points="23 4 23 10 17 10" />
      <path d="M20.49 15a9 9 0 1 1-2.12-9.36L23 10" />
    </>
  ),
  bell: (
    <>
      <path d="M18 8A6 6 0 0 0 6 8c0 7-3 9-3 9h18s-3-2-3-9" />
      <path d="M13.73 21a2 2 0 0 1-3.46 0" />
    </>
  ),
  file: (
    <>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
    </>
  ),
  search: (
    <>
      <circle cx="11" cy="11" r="7" />
      <path d="m21 21-4.3-4.3" />
    </>
  ),
  settings: (
    <>
      <circle cx="12" cy="12" r="3" />
      <path d="M19.4 15a1.65 1.65 0 0 0 .33 1.82l.06.06a2 2 0 1 1-2.83 2.83l-.06-.06a1.65 1.65 0 0 0-1.82-.33 1.65 1.65 0 0 0-1 1.51V21a2 2 0 0 1-4 0v-.09a1.65 1.65 0 0 0-1-1.51 1.65 1.65 0 0 0-1.82.33l-.06.06a2 2 0 1 1-2.83-2.83l.06-.06a1.65 1.65 0 0 0 .33-1.82 1.65 1.65 0 0 0-1.51-1H3a2 2 0 0 1 0-4h.09a1.65 1.65 0 0 0 1.51-1 1.65 1.65 0 0 0-.33-1.82l-.06-.06a2 2 0 1 1 2.83-2.83l.06.06a1.65 1.65 0 0 0 1.82.33h0a1.65 1.65 0 0 0 1-1.51V3a2 2 0 0 1 4 0v.09a1.65 1.65 0 0 0 1 1.51 1.65 1.65 0 0 0 1.82-.33l.06-.06a2 2 0 1 1 2.83 2.83l-.06.06a1.65 1.65 0 0 0-.33 1.82V9a1.65 1.65 0 0 0 1.51 1H21a2 2 0 0 1 0 4h-.09a1.65 1.65 0 0 0-1.51 1z" />
    </>
  ),
  chevron: <polyline points="9 18 15 12 9 6" />,
  chevronUp: <polyline points="18 15 12 9 6 15" />,
  dots: (
    <>
      <circle cx="12" cy="12" r="1" />
      <circle cx="19" cy="12" r="1" />
      <circle cx="5" cy="12" r="1" />
    </>
  ),
  clock: (
    <>
      <circle cx="12" cy="12" r="10" />
      <polyline points="12 6 12 12 16 14" />
    </>
  ),
  bookmark: <path d="M19 21l-7-5-7 5V5a2 2 0 0 1 2-2h10a2 2 0 0 1 2 2z" />,
  draft: (
    <>
      <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
      <polyline points="14 2 14 8 20 8" />
      <line x1="9" y1="13" x2="15" y2="13" />
      <line x1="9" y1="17" x2="13" y2="17" />
    </>
  ),
  msg: <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />,
  send: (
    <>
      <line x1="22" y1="2" x2="11" y2="13" />
      <polygon points="22 2 15 22 11 13 2 9 22 2" />
    </>
  ),
  paperclip: (
    <path d="M21.44 11.05l-9.19 9.19a6 6 0 0 1-8.49-8.49l9.19-9.19a4 4 0 0 1 5.66 5.66l-9.2 9.19a2 2 0 0 1-2.83-2.83l8.49-8.48" />
  ),
  at: (
    <>
      <circle cx="12" cy="12" r="4" />
      <path d="M16 8v5a3 3 0 0 0 6 0v-1a10 10 0 1 0-3.92 7.94" />
    </>
  ),
  bold: (
    <>
      <path d="M6 4h8a4 4 0 0 1 4 4 4 4 0 0 1-4 4H6z" />
      <path d="M6 12h9a4 4 0 0 1 4 4 4 4 0 0 1-4 4H6z" />
    </>
  ),
  italic: (
    <>
      <line x1="19" y1="4" x2="10" y2="4" />
      <line x1="14" y1="20" x2="5" y2="20" />
      <line x1="15" y1="4" x2="9" y2="20" />
    </>
  ),
  list: (
    <>
      <line x1="8" y1="6" x2="21" y2="6" />
      <line x1="8" y1="12" x2="21" y2="12" />
      <line x1="8" y1="18" x2="21" y2="18" />
      <line x1="3" y1="6" x2="3.01" y2="6" />
      <line x1="3" y1="12" x2="3.01" y2="12" />
      <line x1="3" y1="18" x2="3.01" y2="18" />
    </>
  ),
  link: (
    <>
      <path d="M10 13a5 5 0 0 0 7.54.54l3-3a5 5 0 0 0-7.07-7.07l-1.72 1.71" />
      <path d="M14 11a5 5 0 0 0-7.54-.54l-3 3a5 5 0 0 0 7.07 7.07l1.71-1.71" />
    </>
  ),
  check: <polyline points="20 6 9 17 4 12" />,
  arrowL: <polyline points="15 18 9 12 15 6" />,
  arrowR: <polyline points="9 18 15 12 9 6" />,
  calendar: (
    <>
      <rect x="3" y="4" width="18" height="18" rx="2" ry="2" />
      <line x1="16" y1="2" x2="16" y2="6" />
      <line x1="8" y1="2" x2="8" y2="6" />
      <line x1="3" y1="10" x2="21" y2="10" />
    </>
  ),
  external: (
    <>
      <path d="M18 13v6a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V8a2 2 0 0 1 2-2h6" />
      <polyline points="15 3 21 3 21 9" />
      <line x1="10" y1="14" x2="21" y2="3" />
    </>
  ),
  sparkle: (
    <>
      <path d="M12 3l1.5 4.5L18 9l-4.5 1.5L12 15l-1.5-4.5L6 9l4.5-1.5z" />
      <path d="M19 14l.7 2.1L22 17l-2.3.9L19 20l-.7-2.1L16 17l2.3-.9z" />
    </>
  ),
  plus: (
    <>
      <line x1="12" y1="5" x2="12" y2="19" />
      <line x1="5" y1="12" x2="19" y2="12" />
    </>
  ),
  smile: (
    <>
      <circle cx="12" cy="12" r="10" />
      <path d="M8 14s1.5 2 4 2 4-2 4-2" />
      <line x1="9" y1="9" x2="9.01" y2="9" />
      <line x1="15" y1="9" x2="15.01" y2="9" />
    </>
  ),
  pin: (
    <>
      <line x1="12" y1="17" x2="12" y2="22" />
      <path d="M5 17h14v-1.76a2 2 0 0 0-1.11-1.79l-1.78-.9A2 2 0 0 1 15 10.76V6h1a2 2 0 0 0 0-4H8a2 2 0 0 0 0 4h1v4.76a2 2 0 0 1-1.11 1.79l-1.78.9A2 2 0 0 0 5 15.24z" />
    </>
  ),
  close: (
    <>
      <line x1="18" y1="6" x2="6" y2="18" />
      <line x1="6" y1="6" x2="18" y2="18" />
    </>
  ),
};
