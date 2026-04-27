import { DefinitionCategory } from '@/lib/types/models';

export interface DefaultDefinition {
  term: string;
  category: DefinitionCategory;
  defaultDefinition: string;
}

export const DEFINITION_DEFAULTS: DefaultDefinition[] = [
  {
    term: 'AOV',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'Average Order Value — total revenue divided by number of orders in a given period.',
  },
  {
    term: 'LTV',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'Customer Lifetime Value — total revenue from a customer across all orders, measured from first purchase.',
  },
  {
    term: 'CAC',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'Customer Acquisition Cost — paid-channel spend divided by number of new customers acquired.',
  },
  {
    term: 'Blended CAC',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'All marketing spend (paid + organic) divided by total new customers — the true all-in acquisition cost.',
  },
  {
    term: 'ROAS',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'Return on Ad Spend — revenue attributed to ads divided by ad spend for the same period.',
  },
  {
    term: 'MER',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'Marketing Efficiency Ratio — total revenue divided by total marketing spend across all channels.',
  },
  {
    term: 'Repeat Purchase Rate',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'The percentage of customers who have placed two or more orders within the last 12 months.',
  },
  {
    term: 'First-Purchase Discount Rate',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'The percentage of first-time customers who used a discount code on their initial order.',
  },
  {
    term: 'Refund Rate',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'The percentage of orders that result in a refund or return within 60 days.',
  },
  {
    term: 'Cart Abandonment',
    category: DefinitionCategory.Metric,
    defaultDefinition:
      'The percentage of shopping carts created that are not completed into a paid order.',
  },
  {
    term: 'VIP Threshold',
    category: DefinitionCategory.Segment,
    defaultDefinition:
      'The lifetime-revenue cutoff above which a customer is classified as VIP. Often set at the top 10% of customers by revenue.',
  },
  {
    term: 'New Customer',
    category: DefinitionCategory.Segment,
    defaultDefinition:
      'A customer whose earliest order occurred in the last 30 days and who has not yet placed a repeat order.',
  },
  {
    term: 'Returning Customer',
    category: DefinitionCategory.Segment,
    defaultDefinition:
      'A customer with two or more orders, with the most recent order within the last 90 days.',
  },
  {
    term: 'Subscribe & Save Customer',
    category: DefinitionCategory.Segment,
    defaultDefinition:
      'A customer currently enrolled in an active recurring subscription with at least one fulfilled delivery.',
  },
];

export const CATEGORY_LABELS: Record<DefinitionCategory, string> = {
  [DefinitionCategory.Metric]: 'Metrics',
  [DefinitionCategory.Segment]: 'Segments',
  [DefinitionCategory.Status]: 'Statuses',
  [DefinitionCategory.LifecycleStage]: 'Lifecycle Stages',
  [DefinitionCategory.Custom]: 'Custom',
};

export const CATEGORY_HELP_TEXT: Record<DefinitionCategory, string> = {
  [DefinitionCategory.Metric]:
    'How you measure performance. These often mean different things at different companies.',
  [DefinitionCategory.Segment]:
    'How you group your customers. Your segments reflect your business model.',
  [DefinitionCategory.Status]:
    'Active states that define where a customer is right now.',
  [DefinitionCategory.LifecycleStage]:
    'The journey from prospect to loyal customer.',
  [DefinitionCategory.Custom]:
    "Your own terms that don't fit the categories above.",
};

export const CATEGORY_ORDER: DefinitionCategory[] = [
  DefinitionCategory.Metric,
  DefinitionCategory.Segment,
  DefinitionCategory.Status,
  DefinitionCategory.LifecycleStage,
  DefinitionCategory.Custom,
];
