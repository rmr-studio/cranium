export type SignalState = 'live' | 'watch' | 'resolved';
export type SignalSeverity = 'high' | 'med' | 'low' | 'info';

export interface Signal {
  id: string;
  state: SignalState;
  severity: SignalSeverity;
  title: string;
  source: string;
  time: string;
  unread?: boolean;
}

export const SIGNALS: Signal[] = [
  {
    id: 'sig-2041',
    state: 'live',
    severity: 'high',
    unread: true,
    title: 'Demand spike · Linen Field Tee',
    source: 'Shopify · Intercom · Klaviyo',
    time: '14m ago',
  },
  {
    id: 'sig-2040',
    state: 'live',
    severity: 'med',
    title: 'Refund rate above target',
    source: 'Stripe · Gorgias',
    time: '47m ago',
  },
  {
    id: 'sig-2039',
    state: 'live',
    severity: 'low',
    title: 'CAC drift · Meta retargeting',
    source: 'Meta · GA4',
    time: '2h ago',
  },
  {
    id: 'sig-2038',
    state: 'watch',
    severity: 'med',
    unread: true,
    title: 'Inventory cover < 14d · 4 SKUs',
    source: 'Shopify',
    time: '3h ago',
  },
  {
    id: 'sig-2037',
    state: 'watch',
    severity: 'low',
    title: 'NPS detractor cluster · sizing',
    source: 'Delighted · Gorgias',
    time: 'Yesterday',
  },
  {
    id: 'sig-2031',
    state: 'resolved',
    severity: 'med',
    title: 'Cart abandonment spike',
    source: 'Shopify',
    time: '2d ago',
  },
];

export const RAIL_ITEMS = [
  { id: 'home', label: 'Home', icon: 'grid' as const },
  { id: 'signals', label: 'Signals', icon: 'bell' as const },
  { id: 'records', label: 'Records', icon: 'table' as const },
  { id: 'flows', label: 'Flows', icon: 'flow' as const },
  { id: 'notes', label: 'Notes', icon: 'file' as const },
];
