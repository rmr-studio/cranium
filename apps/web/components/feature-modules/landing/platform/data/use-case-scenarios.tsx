import {
  AlertTriangle,
  BarChart3,
  BrainCircuit,
  Crown,
  Globe,
  Inbox,
  Megaphone,
  PiggyBank,
  Scale,
  Sparkles,
  TrendingUp,
  Users,
  Workflow,
} from 'lucide-react';

import {
  BrandFacebook,
  BrandGoogle,
  BrandGorgias,
  BrandKlaviyo,
  BrandShopify,
  BrandStripe,
} from '@/components/ui/diagrams/brand-icons';
import { PlatformChip } from '@/components/ui/diagrams/brand-ui-primitives';

import type { AgentScenario } from './use-case-types';

// ── UC1: Feedback Intelligence → Agentic Product Ops ──────────────────

const feedbackScenario: AgentScenario = {
  id: 'feedback',
  key: 'feedback',
  version: 'V1',
  tabIcon: <Inbox className="size-4" />,
  tabTitle: 'Feedback Intelligence',
  tabSubtitle: 'Agentic Product Ops',
  skills: [
    {
      icon: <BrandGorgias size={16} />,
      title: 'Cluster feedback themes',
      bullets: [
        <>
          When <PlatformChip icon={<BrandGorgias size={10} />} label="tickets" /> spike{' '}
          <PlatformChip icon={<AlertTriangle className="size-2.5" />} label=">3σ WoW" />
        </>,
        <>
          Cluster themes across <PlatformChip icon={<BrandShopify size={10} />} label="orders" />{' '}
          and NPS detractors
        </>,
        <>
          Flag <strong>packaging damage</strong> as top driver (68% of tickets)
        </>,
      ],
    },
    {
      icon: <Crown className="size-4 text-amber-500" />,
      title: 'Tag VIP + churn-risk',
      bullets: [
        <>
          When <PlatformChip icon={<BrandShopify size={10} />} label="customer" /> matches AOV &gt;
          $150, 5+ orders
        </>,
        <>
          Apply <PlatformChip icon={<Crown className="size-2.5" />} label="VIP" /> tag, route to
          senior CS in <PlatformChip icon={<BrandGorgias size={10} />} label="Gorgias" />
        </>,
        <>
          Cross-reference <PlatformChip icon={<BrandStripe size={10} />} label="Stripe" /> refund
          history for churn risk
        </>,
      ],
    },
    {
      icon: <Sparkles className="size-4 text-foreground" />,
      title: 'Research + action brief',
      bullets: [
        <>
          Research{' '}
          <PlatformChip icon={<Globe className="size-2.5" />} label="competitor packaging" /> with
          web agent
        </>,
        <>
          Estimate COGS impact <strong>+$0.14/unit</strong> and retained revenue
        </>,
        <>
          Draft brief, queue{' '}
          <PlatformChip icon={<BrandKlaviyo size={10} />} label="Klaviyo apology flow" /> on
          approval
        </>,
      ],
    },
    {
      icon: <Workflow className="size-4" />,
      title: 'Orchestrate with HITL',
      bullets: [
        <>
          Create ops task, draft supplier RFP, update{' '}
          <PlatformChip icon={<BrandGorgias size={10} />} label="CS FAQ" />
        </>,
        <>
          Schedule 30-day review; re-run{' '}
          <PlatformChip icon={<Sparkles className="size-2.5" />} label="agent" /> on new signal
        </>,
        <>
          HITL gradient: <strong>auto / weekly / per-change</strong> approvals
        </>,
      ],
    },
  ],
  kbQuery: (
    <>
      What is driving the packaging complaint spike across{' '}
      <PlatformChip icon={<BrandGorgias size={11} />} label="Gorgias" /> and NPS this week?
    </>
  ),
  kbRetrieved: [
    'Gorgias · 47 tickets',
    'Yotpo · 12 reviews',
    'NPS · 3 detractors',
    'Shopify · orders',
  ],
  kbAnalysedTitle: <>Packaging complaints concentrated in VIP + churn-risk cohort</>,
  kbAnalysedCards: [
    { icon: <BrandGorgias size={12} />, title: '47 tickets', detail: '+340% WoW' },
    { icon: <Crown className="size-3" />, title: '3 VIPs hit', detail: 'AOV > $150, 5+ orders' },
  ],
  kbIdentified: 'Q1 lightweight packaging change',
  kbIntegrations: [
    { icon: <BrandGorgias size={48} />, label: 'Gorgias' },
    { icon: <BrandShopify size={48} />, label: 'Shopify' },
    { icon: <BrandKlaviyo size={48} />, label: 'Klaviyo' },
    { icon: <BrandStripe size={48} />, label: 'Stripe' },
  ],
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        The Q1 switch to lightweight packaging correlates with the spike. 68% of tickets mention
        damaged arrival, and <strong>3 VIP accounts</strong> plus 4 churn-risk accounts are
        impacted. Estimated exposure: <strong>$42K–$67K/yr</strong> in replacement + VIP retention.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Proposed action brief</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Switch to corrugated inserts for orders{' '}
          <PlatformChip icon={<BrandShopify size={10} />} label=">$75" />. COGS impact{' '}
          <strong>+$0.14/unit</strong>. Auto-escalate VIP tickets to senior CS; draft personalised
          outreach for the 4 churn-risk accounts.
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Orchestrated on approval</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Creates ops task, drafts supplier RFP, updates CS FAQ, queues a{' '}
          <PlatformChip icon={<BrandKlaviyo size={10} />} label="Klaviyo" /> apology flow, and
          schedules a 30-day review.
        </p>
      </div>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>Review brief</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};

// ── UC2: Campaign Intelligence → Auto-Optimisation ────────────────────

const campaignScenario: AgentScenario = {
  id: 'campaign',
  key: 'campaign',
  version: 'V2',
  tabIcon: <Megaphone className="size-4" />,
  tabTitle: 'Campaign Intelligence',
  tabSubtitle: 'Competitive scraping, auto-optimise',
  skills: [
    {
      icon: <BrandFacebook size={16} />,
      title: 'Detect creative fatigue',
      bullets: [
        <>
          When <PlatformChip icon={<BrandFacebook size={10} />} label="Meta" /> CTR decays &gt;3%
          /week for 2+ weeks
        </>,
        <>
          Compare vs <PlatformChip icon={<BrandGoogle size={10} />} label="Google" /> and TikTok
          baselines
        </>,
        <>
          Flag campaigns for pruning;{' '}
          <PlatformChip icon={<BarChart3 className="size-2.5" />} label="CPA +31%" />
        </>,
      ],
    },
    {
      icon: <Sparkles className="size-4 text-foreground" />,
      title: 'Competitor research agent',
      bullets: [
        <>
          Scrape <PlatformChip icon={<Globe className="size-2.5" />} label="Ad Library" /> for
          competitor X UGC assets
        </>,
        <>
          Web <PlatformChip icon={<Sparkles className="size-2.5" />} label="agent" /> summarises
          trending creative patterns
        </>,
        <>
          Generate 5-asset <strong>UGC creator brief</strong> on brand voice
        </>,
      ],
    },
    {
      icon: <Scale className="size-4" />,
      title: 'Reallocate cross-channel',
      bullets: [
        <>
          Shift <strong>30%</strong>{' '}
          <PlatformChip icon={<BrandFacebook size={10} />} label="Meta" /> → TikTok by ROAS weight
        </>,
        <>
          Launch 14-day A/B;{' '}
          <PlatformChip icon={<TrendingUp className="size-2.5" />} label="ROAS 3.1x" /> projected
        </>,
        <>
          Report attribution back to{' '}
          <PlatformChip icon={<BrandShopify size={10} />} label="Shopify" />
        </>,
      ],
    },
  ],
  kbQuery: (
    <>
      Why is <PlatformChip icon={<BrandFacebook size={11} />} label="Meta" /> CPA climbing on Summer
      Launch and where should budget go?
    </>
  ),
  kbRetrieved: [
    'Meta · Summer Launch',
    'Google · brand + non-brand',
    'TikTok · 30d trend',
    'Ad Library · competitor X',
  ],
  kbAnalysedTitle: <>Creative fatigue signal + channel efficiency shift</>,
  kbAnalysedCards: [
    {
      icon: <BarChart3 className="size-3" />,
      title: 'CTR -4.2% WoW',
      detail: '3 consecutive weeks',
    },
    { icon: <TrendingUp className="size-3" />, title: 'TikTok ROAS +18%', detail: 'last 30d' },
  ],
  kbIdentified: 'Shift 15–30% Meta → TikTok + UGC',
  kbIntegrations: [
    { icon: <BrandFacebook size={48} />, label: 'Meta' },
    { icon: <BrandGoogle size={48} />, label: 'Google Ads' },
    { icon: <BrandShopify size={48} />, label: 'Shopify' },
  ],
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        Summer Launch matches a classic fatigue signal: CTR decay{' '}
        <strong>&gt;3%/week for 2+ weeks</strong>, CPA <strong>+31%</strong>. Competitor X launched
        UGC three weeks ago; industry-wide studio creative is declining.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Proposed reallocation</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Shift <strong>30%</strong> of{' '}
          <PlatformChip icon={<BrandFacebook size={10} />} label="Meta" /> spend to TikTok, pause
          bottom 3 creatives, draft a creator brief for 5 UGC assets, A/B test for 14 days. HITL
          gradient: OK, 15% instead of 30%? Weekly proposals or auto-execute?
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Expected impact (14d)</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          UGC variants: <strong>CTR +67%, CPA -22%</strong>. TikTok reallocation ROAS{' '}
          <strong>3.1x</strong> vs Meta <strong>1.8x</strong>. Blended CPA improves{' '}
          <strong>18%</strong>, statistically significant.
        </p>
      </div>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>Review reallocation</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};

// ── UC3: Cohort Retention Orchestration ───────────────────────────────

const cohortScenario: AgentScenario = {
  id: 'cohort',
  key: 'cohort',
  version: 'V3',
  tabIcon: <Users className="size-4" />,
  tabTitle: 'Cohort Retention',
  tabSubtitle: 'Segment, salvage, win-back',
  skills: [
    {
      icon: <BrandShopify size={16} />,
      title: 'Segment + predict LTV',
      bullets: [
        <>
          Join <PlatformChip icon={<BrandShopify size={10} />} label="Shopify" /> ×{' '}
          <PlatformChip icon={<BrandKlaviyo size={10} />} label="Klaviyo" /> × Recharge
        </>,
        <>
          Predict <strong>LTV + churn</strong> for discount-acquired cohorts
        </>,
        <>
          Flag <PlatformChip icon={<Users className="size-2.5" />} label="30OFF-JAN" /> as 40% below
          baseline
        </>,
      ],
    },
    {
      icon: <BrandKlaviyo size={16} />,
      title: 'Author win-back flows',
      bullets: [
        <>
          When 60-day repeat &lt;20%, author education flow via{' '}
          <PlatformChip icon={<BrandKlaviyo size={10} />} label="Klaviyo" />
        </>,
        <>
          <strong>Never re-discount</strong>; clarify subscription terms instead
        </>,
        <>Personalise outreach for high-AOV salvageables; queue 90-day win-back</>,
      ],
    },
    {
      icon: <Sparkles className="size-4 text-foreground" />,
      title: 'Finance counsel agent',
      bullets: [
        <>
          Agent audits <PlatformChip icon={<BrandStripe size={10} />} label="Stripe" /> LTV vs CAC
          depth by cohort
        </>,
        <>
          Research <PlatformChip icon={<Globe className="size-2.5" />} label="benchmarks" /> for
          discount-depth policy
        </>,
        <>
          Flag <PlatformChip icon={<PiggyBank className="size-2.5" />} label="$14K write-down" /> to
          finance with brief
        </>,
      ],
    },
    {
      icon: <BrainCircuit className="size-4" />,
      title: 'Health-metric monitoring',
      bullets: [
        <>
          Track 60d repeat, pause rate,{' '}
          <PlatformChip icon={<BrandKlaviyo size={10} />} label="open rate" /> rolling baselines
        </>,
        <>Alert on drift &gt;2σ; auto-link to owning cohort</>,
        <>Re-run segmentation when drift persists 2+ weeks</>,
      ],
    },
  ],
  kbQuery: (
    <>
      Why is the <PlatformChip icon={<BrandShopify size={11} />} label="Q1 cohort" /> churning and
      what should we do?
    </>
  ),
  kbRetrieved: [
    'Shopify · Q1 cohort',
    'Klaviyo · open rates',
    'Recharge · subscriptions',
    'Gorgias · ticket themes',
  ],
  kbAnalysedTitle: <>Discount-acquired cohort underperforming across every retention metric</>,
  kbAnalysedCards: [
    { icon: <BrainCircuit className="size-3" />, title: '60d repeat 18%', detail: 'vs 31% avg' },
    { icon: <BrandKlaviyo size={12} />, title: 'Opens 12%', detail: 'vs 24% avg' },
  ],
  kbIdentified: '30OFF-JAN cohort, LTV 40% below baseline',
  kbIntegrations: [
    { icon: <BrandShopify size={48} />, label: 'Shopify' },
    { icon: <BrandKlaviyo size={48} />, label: 'Klaviyo' },
    { icon: <BrandGorgias size={48} />, label: 'Gorgias' },
    { icon: <BrandStripe size={48} />, label: 'Stripe' },
  ],
  kbResponse: (
    <>
      <p className="text-sm leading-relaxed text-foreground/80">
        The Q1 <strong>30OFF-JAN</strong> cohort shows every discount-acquired fingerprint: low
        repeat, high pause rate, low email engagement. 34% of tickets mention{' '}
        <em>&ldquo;didn&rsquo;t realise it was a subscription&rdquo;</em>.
      </p>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Segment + orchestrate</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Salvageable <strong>1,400</strong>: educational{' '}
          <PlatformChip icon={<BrandKlaviyo size={10} />} label="Klaviyo" /> flow (no re-discount),
          add subscription skip button, personalised outreach for high-AOV. Likely-churned{' '}
          <strong>1,800</strong>: 90-day win-back queue, flag{' '}
          <PlatformChip icon={<BrandStripe size={10} />} label="$14K" /> LTV write-down to finance.
        </p>
      </div>

      <div className="rounded-md border-l-2 border-foreground/20 pl-3">
        <p className="text-sm font-semibold text-foreground">Strategic finding</p>
        <p className="mt-1 text-xs leading-relaxed text-muted-foreground">
          Reduce acquisition discount depth. <strong>$8 CPA × 18% repeat = $44</strong> effective
          CAC. <strong>$22 CPA × 31% repeat = $71</strong>. Cheap customers are actually more
          expensive.
        </p>
      </div>

      <div className="flex items-center gap-1.5 pt-1 text-sm font-medium text-foreground">
        <span>Review segment plan</span>
        <span>&rarr;</span>
      </div>
    </>
  ),
};

export const USE_CASES: AgentScenario[] = [feedbackScenario, campaignScenario, cohortScenario];
