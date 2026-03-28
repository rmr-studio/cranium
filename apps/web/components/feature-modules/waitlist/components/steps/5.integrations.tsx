'use client';

import { OkButton } from '@/components/feature-modules/waitlist/components/ok-button';
import { INPUT_CLASS } from '@/components/feature-modules/waitlist/config/steps';
import { cn } from '@/lib/utils';
import { Check, ChevronDown, Search, X } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import { useMemo, useState } from 'react';

type CategoryConfig = {
  label: string;
  options: { key: string; label: string }[];
};

export const INTEGRATIONS_STEP_CONFIG = {
  title: 'What are your most important integrations?',
  subtitle: 'Select your top integrations you want to see here from day 1',
  instruction: 'Choose up to 5',
  maxSelections: 5,
  categories: [
    {
      label: 'Commerce & Payments',
      options: [
        { key: 'shopify', label: 'Shopify' },
        { key: 'stripe', label: 'Stripe' },
        { key: 'recharge', label: 'Recharge' },
        { key: 'woocommerce', label: 'WooCommerce' },
        { key: 'bigcommerce', label: 'BigCommerce' },
      ],
    },
    {
      label: 'Support & CRM',
      options: [
        { key: 'gorgias', label: 'Gorgias' },
        { key: 'zendesk', label: 'Zendesk' },
        { key: 'hubspot', label: 'HubSpot' },
        { key: 'intercom', label: 'Intercom' },
        { key: 'salesforce', label: 'Salesforce' },
        { key: 'attio', label: 'Attio' },
      ],
    },
    {
      label: 'Marketing & Email',
      options: [
        { key: 'klaviyo', label: 'Klaviyo' },
        { key: 'google-ads', label: 'Google Ads' },
        { key: 'meta-ads', label: 'Meta Ads' },
        { key: 'tiktok-ads', label: 'TikTok Ads' },
        { key: 'mailchimp', label: 'Mailchimp' },
        { key: 'postscript', label: 'Postscript' },
        { key: 'attentive', label: 'Attentive' },
      ],
    },
    {
      label: 'Analytics & Data',
      options: [
        { key: 'google-analytics', label: 'Google Analytics (GA4)' },
        { key: 'posthog', label: 'PostHog' },
        { key: 'triple-whale', label: 'Triple Whale' },
        { key: 'lifetimely', label: 'Lifetimely' },
        { key: 'mixpanel', label: 'Mixpanel' },
        { key: 'segment', label: 'Segment' },
      ],
    },
  ] satisfies CategoryConfig[],
};

const ALL_PRESET_LABELS = new Set(
  INTEGRATIONS_STEP_CONFIG.categories.flatMap((c) => c.options.map((o) => o.label)),
);

function CategorySection({
  category,
  filteredOptionKeys,
  selectedIntegrations,
  atLimit,
  onToggle,
  expanded,
  onToggleExpanded,
}: {
  category: CategoryConfig;
  filteredOptionKeys: Set<string> | null;
  selectedIntegrations: string[];
  atLimit: boolean;
  onToggle: (label: string) => void;
  expanded: boolean;
  onToggleExpanded: () => void;
}) {
  const selectedCount = category.options.filter((o) =>
    selectedIntegrations.includes(o.label),
  ).length;

  return (
    <motion.div
      layout
      initial={{ opacity: 0, height: 0 }}
      animate={{ opacity: 1, height: 'auto' }}
      exit={{ opacity: 0, height: 0 }}
      transition={{ duration: 0.2 }}
      className="overflow-hidden"
    >
      <button
        type="button"
        onClick={onToggleExpanded}
        className="flex w-full cursor-pointer items-center gap-2 py-2 text-left"
      >
        <span className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
          {category.label}
        </span>
        {selectedCount > 0 && (
          <span className="rounded-full bg-teal-700/20 px-1.5 py-0.5 text-[10px] font-medium text-teal-600">
            {selectedCount}
          </span>
        )}
        <ChevronDown
          className={cn(
            'ml-auto h-3.5 w-3.5 text-muted-foreground transition-transform duration-200',
            expanded && 'rotate-180',
          )}
        />
      </button>
      <AnimatePresence initial={false}>
        {expanded && (
          <motion.div
            initial={{ height: 0, opacity: 0 }}
            animate={{ height: 'auto', opacity: 1 }}
            exit={{ height: 0, opacity: 0 }}
            transition={{ duration: 0.2 }}
            className="overflow-hidden"
          >
            <div className="flex flex-wrap gap-2 pb-3">
              <AnimatePresence initial={false}>
                {category.options.map((option) => {
                  const visible = !filteredOptionKeys || filteredOptionKeys.has(option.key);
                  if (!visible) return null;
                  const selected = selectedIntegrations.includes(option.label);
                  const disabled = atLimit && !selected;
                  return (
                    <motion.button
                      key={option.key}
                      layout
                      initial={{ opacity: 0, scale: 0.9 }}
                      animate={{ opacity: 1, scale: 1 }}
                      exit={{ opacity: 0, scale: 0.9 }}
                      transition={{ duration: 0.15 }}
                      type="button"
                      onClick={() => !disabled && onToggle(option.label)}
                      disabled={disabled}
                      className={cn(
                        'inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition-colors duration-150',
                        selected
                          ? 'border-foreground/25 bg-foreground/10'
                          : 'border-foreground/10 bg-foreground/[0.03] hover:border-foreground/15 hover:bg-foreground/[0.07]',
                        disabled && 'cursor-not-allowed opacity-40',
                      )}
                    >
                      {option.label}
                      {selected && <Check className="h-3 w-3 text-foreground/60" />}
                    </motion.button>
                  );
                })}
              </AnimatePresence>
            </div>
          </motion.div>
        )}
      </AnimatePresence>
    </motion.div>
  );
}

export function IntegrationsStep({
  selectedIntegrations,
  onToggle,
  onAddCustom,
  onRemoveCustom,
  showOtherInput,
  onToggleOther,
  onNext,
  error,
}: {
  selectedIntegrations: string[];
  onToggle: (label: string) => void;
  onAddCustom: (label: string) => void;
  onRemoveCustom: (label: string) => void;
  showOtherInput: boolean;
  onToggleOther: () => void;
  onNext: () => void;
  error?: string;
}) {
  const [customInput, setCustomInput] = useState('');
  const [searchQuery, setSearchQuery] = useState('');
  const [expandedState, setExpandedState] = useState<Record<string, boolean>>({});
  const customIntegrations = selectedIntegrations.filter((i) => !ALL_PRESET_LABELS.has(i));
  const atLimit = selectedIntegrations.length >= INTEGRATIONS_STEP_CONFIG.maxSelections;

  const isSearching = searchQuery.trim().length > 0;
  const query = searchQuery.trim().toLowerCase();

  const filteredOptionKeys = useMemo(() => {
    if (!isSearching) return null;
    const keys = new Set<string>();
    for (const cat of INTEGRATIONS_STEP_CONFIG.categories) {
      for (const opt of cat.options) {
        if (opt.label.toLowerCase().includes(query) || opt.key.includes(query)) {
          keys.add(opt.key);
        }
      }
    }
    return keys;
  }, [query, isSearching]);

  const matchingCategoryLabels = useMemo(() => {
    if (!isSearching) return null;
    const labels = new Set<string>();
    for (const cat of INTEGRATIONS_STEP_CONFIG.categories) {
      if (cat.label.toLowerCase().includes(query)) {
        labels.add(cat.label);
      }
    }
    return labels;
  }, [query, isSearching]);

  const handleCustomKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      e.stopPropagation();
      const trimmed = customInput.trim();
      if (trimmed && !selectedIntegrations.includes(trimmed) && !atLimit) {
        onAddCustom(trimmed);
        setCustomInput('');
      }
    }
  };

  const isCategoryVisible = (category: CategoryConfig) => {
    if (!isSearching) return true;
    if (matchingCategoryLabels?.has(category.label)) return true;
    return category.options.some((o) => filteredOptionKeys?.has(o.key));
  };

  const getFilteredKeys = (category: CategoryConfig) => {
    if (!isSearching) return null;
    if (matchingCategoryLabels?.has(category.label)) return null;
    return filteredOptionKeys;
  };

  const isCategoryExpanded = (label: string) => {
    if (isSearching) return true;
    return expandedState[label] ?? false;
  };

  const toggleCategory = (label: string) => {
    setExpandedState((prev) => ({ ...prev, [label]: !isCategoryExpanded(label) }));
  };

  const hasResults = !isSearching || INTEGRATIONS_STEP_CONFIG.categories.some(isCategoryVisible);

  return (
    <div className="py-8">
      <div className="flex items-start gap-3">
        <h3 className="text-xl leading-snug font-medium md:text-2xl">
          {INTEGRATIONS_STEP_CONFIG.title}
          <br />
          <span className="text-lg text-muted-foreground md:text-xl">
            {INTEGRATIONS_STEP_CONFIG.subtitle}
          </span>
          <span className="ml-1 text-destructive">*</span>
        </h3>
      </div>
      <p className="mt-6 text-sm text-muted-foreground md:ml-10">
        {INTEGRATIONS_STEP_CONFIG.instruction}
        <span className="ml-2 text-xs text-muted-foreground/60">
          ({selectedIntegrations.length}/{INTEGRATIONS_STEP_CONFIG.maxSelections})
        </span>
      </p>

      {/* Search */}
      <div className="relative mt-4 max-w-sm md:ml-10">
        <Search className="absolute top-1/2 left-0 h-4 w-4 -translate-y-1/2 text-muted-foreground/50" />
        <input
          type="text"
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          placeholder="Search integrations..."
          className={`${INPUT_CLASS} pl-6`}
        />
      </div>

      <div className="mt-4 space-y-1 md:ml-10">
        <AnimatePresence initial={false}>
          {INTEGRATIONS_STEP_CONFIG.categories.map((category) => {
            if (!isCategoryVisible(category)) return null;
            return (
              <CategorySection
                key={category.label}
                category={category}
                filteredOptionKeys={getFilteredKeys(category)}
                selectedIntegrations={selectedIntegrations}
                atLimit={atLimit}
                onToggle={onToggle}
                expanded={isCategoryExpanded(category.label)}
                onToggleExpanded={() => toggleCategory(category.label)}
              />
            );
          })}
        </AnimatePresence>

        <AnimatePresence>
          {!hasResults && (
            <motion.p
              initial={{ opacity: 0 }}
              animate={{ opacity: 1 }}
              exit={{ opacity: 0 }}
              className="py-4 text-sm text-muted-foreground"
            >
              No integrations match &ldquo;{searchQuery.trim()}&rdquo;
            </motion.p>
          )}
        </AnimatePresence>

        {/* "Other" toggle */}
        <div className="pt-1">
          <button
            type="button"
            onClick={onToggleOther}
            className={cn(
              'mt-2 inline-flex cursor-pointer items-center gap-1.5 rounded-full border px-3 py-1.5 text-sm font-medium transition-all duration-150',
              showOtherInput
                ? 'border-foreground/25 bg-foreground/10'
                : 'border-foreground/10 bg-foreground/[0.03] hover:border-foreground/15 hover:bg-foreground/[0.07]',
            )}
          >
            + Other
          </button>
        </div>

        {/* Custom input + chips */}
        <AnimatePresence>
          {showOtherInput && (
            <motion.div
              initial={{ opacity: 0, height: 0 }}
              animate={{ opacity: 1, height: 'auto' }}
              exit={{ opacity: 0, height: 0 }}
              className="space-y-3 overflow-hidden pt-2"
            >
              {customIntegrations.length > 0 && (
                <div className="flex flex-wrap gap-2">
                  {customIntegrations.map((name) => (
                    <span
                      key={name}
                      className="inline-flex items-center gap-1.5 rounded-full border border-foreground/15 bg-foreground/5 px-3 py-1 text-sm"
                    >
                      {name}
                      <button
                        type="button"
                        onClick={() => onRemoveCustom(name)}
                        className="cursor-pointer text-muted-foreground transition-colors hover:text-foreground"
                      >
                        <X className="h-3 w-3" />
                      </button>
                    </span>
                  ))}
                </div>
              )}
              {!atLimit && (
                <input
                  type="text"
                  value={customInput}
                  onChange={(e) => setCustomInput(e.target.value)}
                  onKeyDown={handleCustomKeyDown}
                  placeholder="Type an integration and press Enter"
                  autoFocus
                  className={`${INPUT_CLASS} max-w-xs`}
                />
              )}
            </motion.div>
          )}
        </AnimatePresence>
      </div>
      {error && <p className="mt-3 ml-10 text-xs text-destructive">{error}</p>}
      <div className="mt-6 ml-10 flex items-center justify-end">
        <OkButton onClick={onNext} disabled={selectedIntegrations.length === 0} />
      </div>
    </div>
  );
}
