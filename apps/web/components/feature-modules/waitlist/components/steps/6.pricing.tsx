import { OptionSelectionStep } from '@/components/feature-modules/waitlist/components/steps/option-selection-step';

export const PRICING_STEP_CONFIG = {
  title: 'What would you expect to pay monthly for a platform like this?',
  instruction: 'Choose 1',
  options: [
    { key: 'A', label: 'Under $50/mo' },
    { key: 'B', label: '$50 - $150/mo' },
    { key: 'C', label: '$150 - $300/mo' },
    { key: 'D', label: '$300+/mo' },
  ],
};

export function PricingStep({
  selectedPrice,
  onSelect,
  onNext,
  error,
}: {
  selectedPrice: string;
  onSelect: (label: string) => void;
  onNext: () => void;
  error?: string;
}) {
  return (
    <OptionSelectionStep
      title={PRICING_STEP_CONFIG.title}
      instruction={PRICING_STEP_CONFIG.instruction}
      options={PRICING_STEP_CONFIG.options}
      selectedValue={selectedPrice}
      onSelect={onSelect}
      onSubmit={onNext}
      error={error}
    />
  );
}
