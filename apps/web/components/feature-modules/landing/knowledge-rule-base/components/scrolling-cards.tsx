import { FC } from 'react';

// ── Data ────────────────────────────────────────────────────────────

export interface CardData {
  category: string;
  query: string;
}

export const ROW_1_CARDS: CardData[] = [
  { category: 'Sales', query: 'Who are the most engaged accounts in the last 14 days?' },
  { category: 'Data & Analytics', query: 'Show me revenue and subscription growth.' },
  {
    category: 'Marketing',
    query: 'How many deals or opportunities are in our current campaign from a specific region?',
  },
  { category: 'Product', query: 'Show retention and feature adoption trends this month.' },
  { category: 'Customer Success', query: 'Which accounts have open tickets older than 7 days?' },
  { category: 'Finance', query: 'What is our MRR breakdown by plan tier?' },
];

export const ROW_2_CARDS: CardData[] = [
  {
    category: 'Engineering',
    query: 'How many auth configs or connected accounts exist for provider X?',
  },
  { category: 'Operations', query: 'Which integrations had sync failures this week?' },
  {
    category: 'Retention',
    query: 'Show customers with at least two visits or repeat usage in the last month.',
  },
  { category: 'Analytics', query: 'What is the average time to convert from trial to paid?' },
  {
    category: 'Growth',
    query: 'List accounts with declining engagement over the past quarter.',
  },
  { category: 'Product', query: 'How does performance or reliability compare across regions?' },
];

// ── Components ──────────────────────────────────────────────────────

const QueryCard: FC<CardData> = ({ category, query }) => {
  return (
    <div className="glass-panel dark w-72 shrink-0 rounded-lg border p-4 backdrop-blur-xl">
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
