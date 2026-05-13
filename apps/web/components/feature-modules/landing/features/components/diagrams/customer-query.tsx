import { BrandIcons, Integration } from '@/components/ui/diagrams/brand-icons';
import { cn } from '@/lib/utils';
import { Logo } from '@cranium/ui/logo';

/**
 * Customer Knowledge Query — chat with grounded answer + floating recs overlay.
 *
 * Two stacked layers:
 *  1. Long Q&A chat (user question → Cranium answer with findings + sources).
 *     Naturally clips at the bottom so we get the "scroll for more" feel.
 *  2. Floating "Recommended actions" card overlapping the bottom of the chat
 *     as the secondary action surface.
 */
export function MockCustomerQuery() {
  return (
    <div className="flex h-full w-full flex-col px-3 pb-3 sm:px-4 sm:pb-4">
      <div
        className={cn(
          'relative h-full w-full overflow-hidden rounded-lg border border-border bg-card bg-[radial-gradient(circle,oklch(0_0_0/0.07)_1px,transparent_1.2px)] bg-[length:14px_14px] shadow-md',
        )}
      >
        <Chat />
        <RecsOverlay />
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Chat (clipped at bottom)
// ---------------------------------------------------------------------------

function Chat() {
  return (
    <div className="absolute inset-0 m-8 h-fit w-full overflow-hidden rounded-lg border bg-card p-3 shadow-lg sm:p-4 lg:m-8 lg:w-1/2">
      {/* User question — right aligned bubble */}
      <div className="mb-3 ml-auto flex max-w-[85%] items-start gap-2 rounded-md border border-border bg-background p-2 sm:max-w-[78%] sm:p-2.5">
        <div className="flex-1 text-[10.5px] leading-snug tracking-tight text-heading sm:text-[11.5px]">
          A customer just emailed asking for a refund on their{' '}
          <b className="font-semibold">Linen Field Tee · Sand</b> — it shrank. Repeat customer. How
          do we usually handle this?
        </div>
        <div
          className="flex h-6 w-6 shrink-0 items-center justify-center rounded-md text-[9px] font-semibold text-white sm:h-7 sm:w-7 sm:text-[10px]"
          style={{ background: 'oklch(0.7 0.13 75)' }}
        >
          ER
        </div>
      </div>
      {/* Cranium response head */}
      <div className="mb-2.5 flex items-center gap-2">
        <Logo size={20} />
        <span className="font-display text-[13px] tracking-tight text-heading sm:text-[15px]">
          Cranium
        </span>
        <ToolsPill />
      </div>

      {/* Lead */}
      <p className="mb-2.5 text-[10.5px] leading-snug tracking-tight text-heading sm:text-[11.5px]">
        Short version: this isn’t isolated — <b className="font-semibold">Linen Field Tee · Sand</b>{' '}
        has a known shrink issue, you have an existing playbook, and the recommended response is a
        no-questions replacement plus a 15% credit.
      </p>

      {/* Findings */}
      <Finding
        tone="alarm"
        icon="🚨"
        headline={
          <>
            14 similar refunds this week — all on this SKU.{' '}
            <em className="font-semibold not-italic" style={{ color: 'oklch(0.55 0.18 27)' }}>
              +340% vs trailing 4w.
            </em>
          </>
        }
        detail='All cite "shrinkage after first wash." Same wash batch (lot LFT-W11).'
      />

      <Finding
        tone="warn"
        icon="⚠️"
        headline={
          <>
            This customer is high-LTV.{' '}
            <em className="font-semibold not-italic" style={{ color: 'oklch(0.45 0.15 60)' }}>
              $847 lifetime · 7 orders.
            </em>
          </>
        }
        detail='Falls into the "VIP repeat" segment — your policy allows more latitude.'
      />

      {/* Shopify metrics */}
      <SourceBlock icon="Shopify" label="Shopify · customer + SKU" meta="customer 41208">
        <div className="grid grid-cols-4 gap-px" style={{ background: 'var(--border)' }}>
          <Metric label="LTV" value="$847" />
          <Metric label="Refunds" value="14" hot />
          <Metric label="Return %" value="11.2" hot />
          <Metric label="Margin" value="$22" cool />
        </div>
      </SourceBlock>

      {/* Zendesk tickets */}
      <SourceBlock icon="Gorgias" label="Gorgias · last 7d" meta="14 tickets">
        <div className="px-2.5 pb-1.5">
          <Ticket
            id="#8421"
            quote="Shrunk a full size after one cold wash."
            who="Lana C · same lot"
          />
          <Ticket
            id="#8434"
            quote="Was a perfect fit, now it's a crop top."
            who="Marcus T · 2-time customer"
          />
        </div>
      </SourceBlock>

      {/* Internal memo */}
      <SourceBlock icon="GoogleSheets" label="Internal · memos · meetings" meta="3 references">
        <MemoRow
          accent="oklch(0.55 0.13 145)"
          icon="GoogleSheets"
          title="Memo · Refund & replacement playbook"
          meta="Notion · pinned by Maya"
          quote={
            <>
              <Highlight>
                For VIP repeat customers (LTV &gt; $500, ≥ 3 orders), default to no-questions
                replacement plus a 15% next-order credit.
              </Highlight>{' '}
              Don’t ask for the item back unless cost &gt; $80.
            </>
          }
        />
        <MemoRow
          accent="oklch(0.7 0.13 75)"
          icon="GoogleMeet"
          title="Meeting · Weekly ops · Apr 22"
          meta="5 attendees"
          quote={
            <>
              <Highlight>
                "The Sand colorway lot LFT-W11 has a wash issue — Mira confirmed."
              </Highlight>{' '}
              Every refund on this SKU is auto-approved through end of month.
            </>
          }
        />
      </SourceBlock>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Recommendations overlay
// ---------------------------------------------------------------------------

function RecsOverlay() {
  return (
    <div
      className={cn(
        'absolute z-10 overflow-hidden rounded-lg border border-border bg-card pb-8 md:h-2/5',
        '-bottom-[80%] left-0 md:right-6 md:bottom-6 lg:left-auto lg:w-[58%]',
      )}
      style={{
        boxShadow: '0 24px 48px -12px oklch(0 0 0 / 0.32), 0 8px 16px -8px oklch(0 0 0 / 0.22)',
      }}
    >
      <div
        className="flex items-center gap-2 px-3 py-2.5"
        style={{ background: 'var(--foreground)', color: 'oklch(0.97 0.008 92)' }}
      >
        <div className="flex-1">
          <div className="font-display text-[12px] leading-tight tracking-tight sm:text-[13px]">
            Recommended response &amp; follow-ups
          </div>
          <div className="font-mono text-[8px] tracking-wider opacity-65 sm:text-[9px]">
            RIVEN · GROUNDED IN YOUR PLAYBOOK
          </div>
        </div>
      </div>

      <RecRow
        icon="Gmail"
        title="Send templated apology + free replacement + 15% credit"
        detail="Drafted in Jared's voice · approved Apr 28 template"
        action={{ label: 'Review & send', primary: true }}
      />
      <RecRow
        icon="Shopify"
        title="Auto-approve refund in Shopify · issue replacement"
        detail="Ships from stock · 15% credit · expires 90d"
        action={{ label: 'Run', primary: true }}
        secondary="Edit"
      />
      <RecRow
        icon="Slack"
        title="Notify #merch-ops · tally now 15 on lot LFT-W11"
        detail="Tag @Maya with the new ticket + count"
        action={{ label: 'Send', primary: false }}
      />
    </div>
  );
}

function ToolsPill() {
  return (
    <div className="ml-auto hidden items-center gap-1 rounded-full border border-border bg-card px-2 py-0.5 font-mono text-[9px] text-muted-foreground sm:flex">
      <ToolDot bg="#5E8E3E">S</ToolDot>
      <ToolDot bg="#03363D">Z</ToolDot>
      <ToolDot bg="oklch(0.5 0.13 277)">N</ToolDot>
      <ToolDot bg="#4A154B">#</ToolDot>
      <span className="ml-1">5 sources · 1.6s</span>
    </div>
  );
}

function ToolDot({ bg, children }: { bg: string; children: React.ReactNode }) {
  return (
    <span
      className="flex h-3 w-3 items-center justify-center rounded-[3px] font-mono text-[7px] font-bold text-white"
      style={{ background: bg }}
    >
      {children}
    </span>
  );
}

function Finding({
  tone,
  icon,
  headline,
  detail,
}: {
  tone: 'alarm' | 'warn';
  icon: string;
  headline: React.ReactNode;
  detail: string;
}) {
  const bg = tone === 'alarm' ? 'oklch(0.97 0.025 27)' : 'oklch(0.98 0.025 75)';
  const border = tone === 'alarm' ? 'oklch(0.88 0.06 27)' : 'oklch(0.88 0.08 75)';
  return (
    <div
      className="mb-1.5 flex gap-2 rounded-md border p-2 sm:p-2.5"
      style={{ background: bg, borderColor: border }}
    >
      <span className="text-sm leading-none">{icon}</span>
      <div className="min-w-0 flex-1">
        <div className="text-[10.5px] leading-snug font-semibold tracking-tight text-heading sm:text-[11.5px]">
          {headline}
        </div>
        <div className="mt-1 font-mono text-[9px] leading-tight text-muted-foreground sm:text-[10px]">
          {detail}
        </div>
      </div>
    </div>
  );
}

function SourceBlock({
  icon,
  label,
  meta,
  children,
}: {
  icon: Integration;

  label: string;
  meta: string;
  children: React.ReactNode;
}) {
  const Icon = BrandIcons[icon];
  return (
    <div className="my-2 overflow-hidden rounded-md border border-border bg-card">
      <div className="flex items-center gap-1.5 border-b border-border bg-background px-2.5 py-1.5">
        <span className="flex h-3.5 w-3.5 shrink-0 items-center justify-center rounded-[3px] font-mono text-[8px] font-bold text-white">
          <Icon size={12} />
        </span>
        <span className="font-mono text-[9px] font-semibold tracking-wider text-heading uppercase sm:text-[10px]">
          {label}
        </span>
        <span className="ml-auto truncate font-mono text-[9px] text-muted-foreground sm:text-[10px]">
          {meta}
        </span>
      </div>
      {children}
    </div>
  );
}

function Metric({
  label,
  value,
  hot,
  cool,
}: {
  label: string;
  value: string;
  hot?: boolean;
  cool?: boolean;
}) {
  return (
    <div className="bg-card px-2 py-1.5">
      <div className="font-mono text-[8px] tracking-wider text-muted-foreground uppercase">
        {label}
      </div>
      <div
        className={cn(
          'mt-0.5 font-display text-sm leading-none tracking-tight tabular-nums',
          hot && 'text-[oklch(0.55_0.18_27)]',
          cool && 'text-[oklch(0.55_0.13_145)]',
          !hot && !cool && 'text-heading',
        )}
      >
        {value}
      </div>
    </div>
  );
}

function Ticket({ id, quote, who }: { id: string; quote: string; who: string }) {
  return (
    <div className="grid grid-cols-[40px_1fr_auto] items-center gap-2 border-b border-dashed border-border py-1.5 last:border-b-0">
      <span className="font-mono text-[9px] tracking-wide text-muted-foreground">{id}</span>
      <div className="min-w-0">
        <div className="truncate text-[10.5px] leading-tight font-medium tracking-tight text-heading sm:text-[11px]">
          {quote}
        </div>
        <div className="font-mono text-[9px] text-muted-foreground">{who}</div>
      </div>
      <span
        className="rounded-full border px-1.5 py-px font-mono text-[8px] font-bold tracking-wider uppercase"
        style={{
          background: 'oklch(0.97 0.025 27)',
          color: 'oklch(0.45 0.18 27)',
          borderColor: 'oklch(0.88 0.06 27)',
        }}
      >
        SHRINK
      </span>
    </div>
  );
}

function MemoRow({
  icon,
  title,
  meta,
  quote,
  accent,
}: {
  icon: Integration;
  title: string;
  meta: string;
  quote: React.ReactNode;
  accent: string;
}) {
  const Icon = BrandIcons[icon];
  return (
    <div className="grid grid-cols-[20px_1fr] gap-2 border-b border-dashed border-border px-2.5 py-2 last:border-b-0">
      <span className="flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] font-mono text-[8px] font-bold text-white">
        <Icon size={10} />
      </span>
      <div className="min-w-0">
        <div className="text-[10.5px] leading-tight font-semibold tracking-tight text-heading sm:text-[11.5px]">
          {title}
        </div>
        <div className="mt-0.5 font-mono text-[9px] text-muted-foreground sm:text-[10px]">
          {meta}
        </div>
        <div
          className="mt-1 rounded-r-[3px] border-l-2 bg-background px-2 py-1 font-mono text-[9px] leading-snug text-content sm:text-[10px]"
          style={{ borderLeftColor: accent }}
        >
          {quote}
        </div>
      </div>
    </div>
  );
}

function Highlight({ children }: { children: React.ReactNode }) {
  return (
    <span className="rounded-[2px] px-0.5" style={{ background: 'oklch(0.96 0.04 70)' }}>
      {children}
    </span>
  );
}

function RecRow({
  icon,

  title,
  detail,
  action,
  secondary,
}: {
  icon: Integration;
  title: string;
  detail: string;
  action: { label: string; primary: boolean };
  secondary?: string;
}) {
  const Icon = BrandIcons[icon];
  return (
    <div className="grid grid-cols-[20px_1fr_auto] items-start gap-2 border-b border-dashed border-border px-3 py-2 last:border-b-0">
      <span className="mt-0.5 flex h-4 w-4 shrink-0 items-center justify-center rounded-[3px] font-mono text-[9px] font-bold text-white">
        <Icon size={12} />
      </span>
      <div className="min-w-0">
        <div className="text-[10.5px] leading-snug font-semibold tracking-tight text-heading sm:text-[11.5px]">
          {title}
        </div>
        <div className="mt-0.5 font-mono text-[9px] text-muted-foreground sm:text-[10px]">
          {detail}
        </div>
      </div>
      <div className="flex shrink-0 items-center gap-1">
        {secondary && (
          <span className="hidden font-display text-[10px] text-muted-foreground sm:inline">
            {secondary}
          </span>
        )}
        <span
          className={cn(
            'rounded-[5px] px-2 py-1 font-display text-[10px] font-medium tracking-tight whitespace-nowrap sm:text-[11px]',
            action.primary
              ? 'border text-[oklch(0.97_0.008_92)]'
              : 'border border-border bg-card text-heading',
          )}
          style={
            action.primary
              ? { background: 'var(--foreground)', borderColor: 'var(--foreground)' }
              : undefined
          }
        >
          {action.label}
        </span>
      </div>
    </div>
  );
}
