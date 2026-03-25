import { OptionSelectionStep } from '@/components/feature-modules/waitlist/components/steps/option-selection-step';

export const INVOLVEMENT_STEP_CONFIG = {
  title: 'Want to help shape what we build?',
  subtitle:
    'We are looking for a small group of founding users to provide feedback and help us build the right product. Join a 20-minute call with the founding team, share your needs and feedback, and get early access to the product as soon as it is ready.',
  instruction: 'Choose 1',
  options: [
    { key: 'A', value: 'WAITLIST' as const, label: 'Just the waitlist' },
    { key: 'B', value: 'EARLY_TESTING' as const, label: 'Early testing access' },
    {
      key: 'C',
      value: 'CALL_EARLY_TESTING' as const,
      label: 'Early access + a 20-min call with the team',
    },
  ],
};

export function InvolvementStep({
  selectedInvolvement,
  onSelect,
  onSubmit,
  isPending,
  error,
}: {
  selectedInvolvement: string | undefined;
  onSelect: (value: string) => void;
  onSubmit: () => void;
  isPending: boolean;
  error?: string;
}) {
  return (
    <OptionSelectionStep
      title={INVOLVEMENT_STEP_CONFIG.title}
      subtitle={INVOLVEMENT_STEP_CONFIG.subtitle}
      instruction={INVOLVEMENT_STEP_CONFIG.instruction}
      options={INVOLVEMENT_STEP_CONFIG.options}
      selectedValue={selectedInvolvement}
      onSelect={onSelect}
      onSubmit={onSubmit}
      submitLabel="Submit"
      isSubmitting={isPending}
      error={error}
    />
  );
}
