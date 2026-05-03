import { cn } from '@/lib/utils';

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
          riven-mcp · connected
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
        MCP servers loaded: <span className="text-[oklch(0.78_0.15_145)]">riven</span>,{' '}
        <span className="text-[oklch(0.78_0.15_145)]">shopify</span>,{' '}
        <span className="text-[oklch(0.78_0.15_145)]">klaviyo</span>
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
        <span className="text-[oklch(0.65_0.01_92)]">
          Morning Jared — what are we digging into?
        </span>
      </Line>

      <Spacer />

      <Line>
        <span className="text-[oklch(0.7_0.12_145)]">{'>'}</span>{' '}
        <span className="text-[oklch(0.96_0_0)]">
          60-day repeat purchase rate dropped{' '}
          <span className="text-[oklch(0.85_0.13_95)]">~9pp</span> last month. Where&apos;s the
          bleed? Break it down by acquisition channel.
        </span>
      </Line>

      <Spacer />

      <ClaudeBlock>
        Pulling the last two cohorts from Riven — comparing Mar vs Apr buyers across channels.
      </ClaudeBlock>

      <McpCard
        ns="riven"
        method="analytics.cohort_diff"
        meta="metric=repeat_60d · window=Apr vs Mar 2026"
      >
        <AnalyticsTable />
        <Line className="mt-2 text-[10px] text-[oklch(0.65_0.01_92)] sm:text-[10.5px]">
          5 channels · 12,847 buyers analysed · weighted Δ{' '}
          <span className="text-[oklch(0.78_0.15_25)]">−8.6pp</span>
        </Line>
      </McpCard>

      <ClaudeBlock>
        Klaviyo Flows is doing most of the damage — down 11.8pp on its own. Checking what changed
        upstream.
      </ClaudeBlock>

      <McpCard ns="riven" method="insights.surface" meta="scope=klaviyo · since=2026-04-01">
        <FindingLine
          tone="bad"
          name="Welcome Series"
          detail="paused 11 days ago · −38% flow revenue"
        />
        <FindingLine
          tone="bad"
          name="Repeat-buyer segment"
          detail="email frequency 3.2/wk → 1.1/wk"
        />
        <FindingLine tone="warn" name="Meta Ads CAC" detail="up 22% · prospecting-heavy mix" />
        <FindingLine
          tone="warn"
          name="Klaviyo + Meta overlap"
          detail="cross-touched buyers down 41%"
        />
        <FindingLine
          tone="ok"
          name="Olive Oil 500ml"
          detail="reorder rate stable · not a SKU issue"
        />
      </McpCard>

      <ClaudeBlock>
        The welcome flow is the bleed. Restart it + rebuild the repeat-buyer cadence — model says
        you recover ~70% of the dip inside 2 weeks.
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

function FindingLine({
  tone,
  name,
  detail,
}: {
  tone: 'bad' | 'warn' | 'ok';
  name: string;
  detail: string;
}) {
  const palette = {
    bad: { mark: '●', color: 'oklch(0.72 0.18 25)' },
    warn: { mark: '▲', color: 'oklch(0.82 0.15 85)' },
    ok: { mark: '✓', color: 'oklch(0.78 0.15 145)' },
  }[tone];
  return (
    <Line className="leading-snug">
      <span style={{ color: palette.color }}>{palette.mark}</span>{' '}
      <span style={{ color: palette.color }}>{name}</span>{' '}
      <span style={{ color: 'oklch(0.6 0.01 92)' }}>→ {detail}</span>
    </Line>
  );
}

function AnalyticsTable() {
  const rows = [
    {
      n: '1',
      channel: 'Klaviyo Flows',
      repeat: '22.1%',
      delta: '−11.8pp',
      aov: '$89',
      tone: 'bad' as const,
    },
    {
      n: '2',
      channel: 'Meta Ads',
      repeat: '18.4%',
      delta: '−6.2pp',
      aov: '$74',
      tone: 'bad' as const,
    },
    {
      n: '3',
      channel: 'TikTok',
      repeat: '14.0%',
      delta: '−2.3pp',
      aov: '$63',
      tone: 'warn' as const,
    },
    {
      n: '4',
      channel: 'Email Broadcasts',
      repeat: '19.8%',
      delta: '−0.6pp',
      aov: '$82',
      tone: 'warn' as const,
    },
    {
      n: '5',
      channel: 'Organic / Direct',
      repeat: '31.2%',
      delta: '+1.4pp',
      aov: '$96',
      tone: 'ok' as const,
    },
  ];
  return (
    <div
      className="overflow-hidden rounded-[4px] border text-[10px]"
      style={{ borderColor: 'oklch(0.32 0.01 92)' }}
    >
      <SheetRow head>
        <SheetCell num>#</SheetCell>
        <SheetCell flex={1.6}>channel</SheetCell>
        <SheetCell flex={1}>repeat 60d</SheetCell>
        <SheetCell flex={1}>Δ vs Mar</SheetCell>
        <SheetCell flex={0.8} hideOnMobile>
          AOV
        </SheetCell>
      </SheetRow>
      {rows.map((r) => (
        <SheetRow key={r.n}>
          <SheetCell num>{r.n}</SheetCell>
          <SheetCell flex={1.6}>{r.channel}</SheetCell>
          <SheetCell flex={1}>{r.repeat}</SheetCell>
          <SheetCell flex={1} tone={r.tone}>
            {r.delta}
          </SheetCell>
          <SheetCell flex={0.8} hideOnMobile>
            {r.aov}
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
  tone,
}: {
  children: React.ReactNode;
  num?: boolean;
  flex?: number;
  hideOnMobile?: boolean;
  tone?: 'bad' | 'warn' | 'ok';
}) {
  const toneColor =
    tone === 'bad'
      ? 'oklch(0.72 0.18 25)'
      : tone === 'warn'
        ? 'oklch(0.82 0.15 85)'
        : tone === 'ok'
          ? 'oklch(0.78 0.15 145)'
          : undefined;
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
        color: num ? 'oklch(0.6 0.01 92)' : (toneColor ?? 'oklch(0.85 0 0)'),
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
