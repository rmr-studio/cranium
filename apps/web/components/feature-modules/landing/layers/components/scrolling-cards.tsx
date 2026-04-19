import { FC } from 'react';

// ── Data ────────────────────────────────────────────────────────────

export interface CardData {
  category: string;
  query: string;
}

export const ROW_1_CARDS: CardData[] = [
  { category: 'Growth', query: 'Which acquisition channel saw CAC spike 20%+ this week?' },
  {
    category: 'Retention',
    query: 'Flag customers whose reorder cadence has slipped past their usual window.',
  },
  {
    category: 'Merchandising',
    query: 'Which SKUs are trending up in returns for sizing or fit complaints?',
  },
  {
    category: 'Customer Experience',
    query: 'Surface the top three complaint themes from this week\'s support tickets.',
  },
  {
    category: 'Paid Media',
    query: 'Which ad creatives have fatigued — CTR down 30% over the last 7 days?',
  },
  {
    category: 'Lifecycle',
    query: 'Who abandoned checkout twice this week and hasn\'t had a follow-up?',
  },
];

export const ROW_2_CARDS: CardData[] = [
  {
    category: 'Inventory',
    query: 'Which hero SKUs will stock out in under 10 days at current sell-through?',
  },
  {
    category: 'Reviews',
    query: 'Where is review sentiment shifting on products launched this quarter?',
  },
  {
    category: 'Subscription',
    query: 'Which subscribers skipped or paused twice in a row — at risk of churn?',
  },
  {
    category: 'Pricing',
    query: 'Where are competitors undercutting our bestsellers right now?',
  },
  {
    category: 'Loyalty',
    query: 'Identify VIPs whose 30-day spend dropped 40% versus their trailing average.',
  },
  {
    category: 'Fulfilment',
    query: 'Which shipping lanes are creeping past SLA and driving WISMO tickets?',
  },
];

// ── Components ──────────────────────────────────────────────────────

const QueryCard: FC<CardData> = ({ category, query }) => {
  return (
    <div className="w-72 shrink-0 rounded-lg p-4 backdrop-blur-xl">
      <p className="font-display text-xs font-bold tracking-widest text-primary uppercase">
        {category}
      </p>
      <p className="mt-1.5 text-sm leading-snug text-content">{query}</p>
    </div>
  );
};

interface ScrollingRowProps {
  cards: CardData[];
  direction: 'left' | 'right';
  duration?: number;
}

export const ScrollingRow: FC<ScrollingRowProps> = ({ cards, direction, duration = 50 }) => {
  return (
    <div className="overflow-hidden">
      <div
        className="flex w-max gap-4"
        style={{
          animation: `scroll-${direction} ${duration}s linear infinite`,
        }}
      >
        {[0, 1].map((copy) => cards.map((card, i) => <QueryCard key={`${copy}-${i}`} {...card} />))}
      </div>
    </div>
  );
};
