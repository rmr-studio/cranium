import { BusinessType, DefinitionCategory } from '@/lib/types/models';

export interface DefaultDefinition {
  term: string;
  category: DefinitionCategory;
  defaultDefinition: string;
}

export const DEFINITION_DEFAULTS: Record<string, DefaultDefinition[]> = {
  [BusinessType.DtcEcommerce]: [
    {
      term: 'Churned Customer',
      category: DefinitionCategory.Metric,
      defaultDefinition:
        'A customer who has not made a purchase in the last 90 days.',
    },
    {
      term: 'Repeat Customer',
      category: DefinitionCategory.Segment,
      defaultDefinition:
        'A customer who has placed 2 or more orders, regardless of time period.',
    },
    {
      term: 'VIP Customer',
      category: DefinitionCategory.Segment,
      defaultDefinition:
        'A customer in the top 10% by lifetime revenue.',
    },
    {
      term: 'Customer Lifetime Value',
      category: DefinitionCategory.Metric,
      defaultDefinition:
        'Total revenue from a customer across all orders, measured from first purchase.',
    },
    {
      term: 'Active Customer',
      category: DefinitionCategory.Status,
      defaultDefinition:
        'A customer who has made at least one purchase in the last 90 days.',
    },
    {
      term: 'Retention Rate',
      category: DefinitionCategory.Metric,
      defaultDefinition:
        'The percentage of customers who made a repeat purchase within 12 months of their first order.',
    },
  ],
  [BusinessType.B2CSaas]: [
    {
      term: 'Churned User',
      category: DefinitionCategory.Metric,
      defaultDefinition:
        'A user whose subscription has been cancelled or expired for more than 30 days.',
    },
    {
      term: 'Active User',
      category: DefinitionCategory.Status,
      defaultDefinition:
        'A user who has logged in and performed at least one core action in the last 30 days.',
    },
    {
      term: 'Net Revenue Retention',
      category: DefinitionCategory.Metric,
      defaultDefinition:
        'Monthly recurring revenue from existing customers this month divided by their MRR 12 months ago, including expansion, contraction, and churn.',
    },
    {
      term: 'Enterprise Customer',
      category: DefinitionCategory.Segment,
      defaultDefinition: 'A customer on an annual plan with 50 or more seats.',
    },
    {
      term: 'Trial Conversion',
      category: DefinitionCategory.Metric,
      defaultDefinition:
        'The percentage of users who convert to a paid plan within 14 days of trial start.',
    },
    {
      term: 'Power User',
      category: DefinitionCategory.Segment,
      defaultDefinition:
        'A user in the top 20% by feature usage frequency over the last 30 days.',
    },
  ],
};

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
