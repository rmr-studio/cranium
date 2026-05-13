import { cn } from '@/lib/utils';

/**
 * Claude Code MCP terminal — captures the spreadsheet→Cranium upload story.
 *
 * Designed to fill its container and clip at the bottom — the visible
 * portion always shows: terminal chrome, the user's /attach command, and
 * the `filesystem::read_xlsx` MCP call with the parsed spreadsheet. That's
 * the load-bearing image for "drop a spreadsheet, MCP does the rest."
 */
export function MockClaudeMcpMutation() {
  return (
    <div className="flex h-full w-full flex-col px-3 pb-3 sm:px-4 sm:pb-4">
      <div
        className={cn(
          'relative w-full overflow-hidden rounded-lg shadow-2xl ring-1 shadow-black/40 ring-white/10',
        )}
        style={{ background: 'oklch(0.18 0.01 92)' }}
      >
        <TerminalChrome />
        <TerminalBody />
      </div>

      <div className="mt-2 hidden items-center gap-3 px-1 font-mono text-[10px] text-muted-foreground sm:flex">
        <Pulse /> cranium-mcp · live
        <span className="ml-auto tabular-nums">3 servers · 17 tools</span>
      </div>
    </div>
  );
}

export function TerminalChrome() {
  return (
    <div
      className="flex items-center gap-2 border-b px-3 py-2"
      style={{
        background: 'oklch(0.13 0.01 92)',
        borderColor: 'oklch(0.28 0.01 92)',
      }}
    >
      <span className="h-2.5 w-2.5 rounded-full" style={{ background: '#ed6a5e' }} />
      <span className="h-2.5 w-2.5 rounded-full" style={{ background: '#f4be4f' }} />
      <span className="h-2.5 w-2.5 rounded-full" style={{ background: '#61c554' }} />
      <span className="ml-2 truncate font-mono text-[10px]" style={{ color: 'oklch(0.7 0.01 92)' }}>
        claude — ~/work/olive-orchard · zsh
      </span>
      <div
        className="ml-auto hidden items-center gap-2 font-mono text-[9px] sm:flex"
        style={{ color: 'oklch(0.55 0.01 92)' }}
      >
        <span className="inline-flex items-center gap-1.5">
          <span
            className="inline-block h-1.5 w-1.5 rounded-full"
            style={{
              background: '#61c554',
              boxShadow: '0 0 0 2.5px oklch(0.7 0.18 145 / 0.18)',
            }}
          />
          cranium-mcp · connected
        </span>
      </div>
    </div>
  );
}

export function TerminalBody() {
  return (
    <div
      className="space-y-1.5 overflow-hidden p-3 font-mono text-[10.5px] leading-snug sm:p-4 sm:text-[11px] lg:text-[12px]"
      style={{ color: 'oklch(0.92 0.01 92)' }}
    >
      <Line className="text-[oklch(0.55_0.01_92)]">Welcome to Claude Code (v2.4.1)</Line>
      <Line className="text-[oklch(0.55_0.01_92)]">
        MCP servers loaded: <span className="text-[oklch(0.78_0.15_145)]">cranium</span>,{' '}
        <span className="text-[oklch(0.78_0.15_145)]">shopify</span>,{' '}
        <span className="text-[oklch(0.78_0.15_145)]">filesystem</span>
      </Line>

      <Spacer />

      <Line>
        <span className="text-[oklch(0.7_0.1_200)]">~/olive-orchard</span>{' '}
        <span className="text-[oklch(0.95_0_0)]">❯</span>{' '}
        <span className="text-[oklch(0.96_0_0)]">claude</span>
      </Line>

      <Spacer />

      <Line>
        <ClaudeTag />
        <span className="text-[oklch(0.65_0.01_92)]">Hey Jared — what are we doing?</span>
      </Line>

      <Spacer />

      <Line>
        <span className="text-[oklch(0.7_0.12_145)]">{'>'}</span>{' '}
        <span className="text-[oklch(0.96_0_0)]">
          Pull our supplier tracker into Cranium — add suppliers as entities and update lead-time +
          MOQ rules. <span className="text-[oklch(0.85_0.13_95)]">/attach ./suppliers-Q2.xlsx</span>
        </span>
      </Line>

      <Spacer />

      <ClaudeBlock>Opening the workbook — 3 sheets: Suppliers, Lead Times, Notes.</ClaudeBlock>

      <McpCard ns="filesystem" method="read_xlsx" meta='suppliers-Q2.xlsx · sheet "Suppliers"'>
        <SheetTable />
        <Line className="mt-2 text-[10px] text-[oklch(0.65_0.01_92)] sm:text-[10.5px]">
          5 suppliers parsed · sheets &quot;Lead Times&quot; (28 rows) and &quot;Notes&quot; (12
          rows) detected
        </Line>
      </McpCard>

      <ClaudeBlock>
        Cross-checking against Cranium — 2 of these already exist as entities, 3 are new. The Notes
        sheet has rules I&apos;ll lift into the glossary.
      </ClaudeBlock>

      <McpCard
        ns="cranium"
        method="entities.upsert_batch"
        meta="5 suppliers · author=Jared@olive-orchard.co"
      >
        <CheckLine name="Anvar Knits" detail="created · 14 SKUs auto-linked" />
        <CheckLine name="Hojo Print Co." detail="created · backup for screen-print" />
        <CheckLine name="Kestrel Trim" detail="created · onboarding queued" />
        <CheckLine name="Mira Textiles" detail="lead_time 28d→32d · MOQ 500u→600u" />
        <CheckLine name="Soller Pack" detail="no changes · already current" />
      </McpCard>

      <ClaudeBlock>
        Tracker is in. Your spreadsheet is the system of record now — Cranium keeps it in sync from
        here.
      </ClaudeBlock>

      <Line>
        <span className="text-[oklch(0.7_0.1_200)]">~/olive-orchard</span>{' '}
        <span className="text-[oklch(0.95_0_0)]">❯</span> <Caret />
      </Line>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------

function Line({ children, className }: { children: React.ReactNode; className?: string }) {
  return <p className={cn('m-0 whitespace-pre-wrap', className)}>{children}</p>;
}

function Spacer() {
  return <div className="h-1" />;
}

function ClaudeTag() {
  return (
    <span
      className="mr-1.5 inline-block rounded-[3px] px-1.5 py-px font-mono text-[9px] font-bold tracking-wider"
      style={{
        background: 'oklch(0.78 0.12 348 / 0.18)',
        color: 'oklch(0.85 0.1 348)',
      }}
    >
      CLAUDE
    </span>
  );
}

function ClaudeBlock({ children }: { children: React.ReactNode }) {
  return (
    <div className="my-1.5 py-0.5 pl-2.5" style={{ borderLeft: '2px solid oklch(0.78 0.12 348)' }}>
      <Line>
        <ClaudeTag />
        <span className="text-[oklch(0.65_0.01_92)]">{children}</span>
      </Line>
    </div>
  );
}

function McpCard({
  ns,
  method,
  meta,
  children,
}: {
  ns: string;
  method: string;
  meta: string;
  children: React.ReactNode;
}) {
  return (
    <div
      className="my-2 overflow-hidden rounded-md border"
      style={{
        background: 'oklch(0.22 0.02 240)',
        borderColor: 'oklch(0.32 0.02 240)',
      }}
    >
      <div
        className="flex items-center gap-2 px-2.5 py-1.5 text-[10px] sm:text-[11px]"
        style={{ background: 'oklch(0.26 0.03 240)' }}
      >
        <span className="font-semibold text-white">
          {ns}
          <span style={{ color: 'oklch(0.7 0.13 240)' }}>::{method}</span>
        </span>
        <span
          className="hidden truncate font-mono text-[10px] sm:inline"
          style={{ color: 'oklch(0.7 0.01 92)' }}
        >
          {meta}
        </span>
        <span
          className="ml-auto rounded-[3px] px-1.5 py-px font-mono text-[9px] font-bold tracking-wider text-white"
          style={{ background: 'oklch(0.4 0.15 145)' }}
        >
          200 OK
        </span>
      </div>
      <div className="px-2.5 py-2 text-[10px] sm:text-[11px]">{children}</div>
    </div>
  );
}

function CheckLine({ name, detail }: { name: string; detail: string }) {
  return (
    <Line className="leading-snug">
      <span style={{ color: 'oklch(0.78 0.15 145)' }}>✓</span>{' '}
      <span style={{ color: 'oklch(0.78 0.15 145)' }}>{name}</span>{' '}
      <span style={{ color: 'oklch(0.6 0.01 92)' }}>→ {detail}</span>
    </Line>
  );
}

function SheetTable() {
  const rows = [
    {
      n: '1',
      supplier: 'Mira Textiles',
      cat: 'linen, cotton',
      terms: '32d · 600u · net-45',
      status: 'primary',
    },
    {
      n: '2',
      supplier: 'Anvar Knits',
      cat: 'jersey, sweats',
      terms: '21d · 300u · net-30',
      status: 'primary',
    },
    {
      n: '3',
      supplier: 'Hojo Print Co.',
      cat: 'screen-print',
      terms: '9d · 50u · net-15',
      status: 'backup',
    },
    {
      n: '4',
      supplier: 'Soller Pack',
      cat: 'mailers, tissue',
      terms: '14d · 1000u · net-30',
      status: 'primary',
    },
    {
      n: '5',
      supplier: 'Kestrel Trim',
      cat: 'labels, hangtags',
      terms: '7d · 200u · net-30',
      status: 'new',
    },
  ];
  return (
    <div
      className="overflow-hidden rounded-[4px] border text-[10px]"
      style={{ borderColor: 'oklch(0.32 0.01 92)' }}
    >
      <SheetRow head>
        <SheetCell num>#</SheetCell>
        <SheetCell flex={1.4}>supplier</SheetCell>
        <SheetCell flex={1} hideOnMobile>
          category
        </SheetCell>
        <SheetCell flex={2}>lead · MOQ · terms</SheetCell>
        <SheetCell flex={0.8} hideOnMobile>
          status
        </SheetCell>
      </SheetRow>
      {rows.map((r) => (
        <SheetRow key={r.n}>
          <SheetCell num>{r.n}</SheetCell>
          <SheetCell flex={1.4}>{r.supplier}</SheetCell>
          <SheetCell flex={1} hideOnMobile>
            {r.cat}
          </SheetCell>
          <SheetCell flex={2}>{r.terms}</SheetCell>
          <SheetCell flex={0.8} hideOnMobile>
            {r.status}
          </SheetCell>
        </SheetRow>
      ))}
    </div>
  );
}

function SheetRow({ head, children }: { head?: boolean; children: React.ReactNode }) {
  return (
    <div
      className="flex border-b last:border-b-0"
      style={{
        background: head ? 'oklch(0.24 0.02 145)' : undefined,
        borderColor: 'oklch(0.28 0.01 92)',
      }}
    >
      {children}
    </div>
  );
}

function SheetCell({
  children,
  num,
  flex = 1,
  hideOnMobile,
}: {
  children: React.ReactNode;
  num?: boolean;
  flex?: number;
  hideOnMobile?: boolean;
}) {
  return (
    <div
      className={cn(
        'truncate border-r px-1.5 py-1 last:border-r-0',
        num && 'w-7 shrink-0 text-center',
        hideOnMobile && 'hidden sm:block',
      )}
      style={{
        flex: num ? undefined : flex,
        background: num ? 'oklch(0.22 0.01 92)' : undefined,
        color: num ? 'oklch(0.6 0.01 92)' : 'oklch(0.85 0 0)',
        borderColor: 'oklch(0.28 0.01 92)',
      }}
    >
      {children}
    </div>
  );
}

function Caret() {
  return (
    <span
      className="inline-block h-3 w-1.5 align-text-bottom"
      style={{
        background: 'oklch(0.92 0.01 92)',
        animation: 'mcp-blink 1.1s steps(1) infinite',
      }}
    >
      <style>{`@keyframes mcp-blink { 50% { opacity: 0; } }`}</style>
    </span>
  );
}

function Pulse() {
  return (
    <span
      className="inline-block h-1.5 w-1.5 rounded-full"
      style={{
        background: 'oklch(0.65 0.17 145)',
        boxShadow: '0 0 0 2.5px oklch(0.65 0.17 145 / 0.2)',
      }}
    />
  );
}
