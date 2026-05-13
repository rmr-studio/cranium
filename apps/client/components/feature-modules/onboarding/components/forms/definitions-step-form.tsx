'use client';

import { Button } from '@cranium/ui/button';
import { Input } from '@cranium/ui/input';
import { Textarea } from '@/components/ui/textarea';
import {
  Collapsible,
  CollapsibleContent,
  CollapsibleTrigger,
} from '@/components/ui/collapsible';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { DefinitionCategory } from '@/lib/types/workspace';
import { cn } from '@cranium/utils';
import { ChevronDown, Plus, RotateCcw, X } from 'lucide-react';
import { FC, useCallback, useEffect, useMemo, useState } from 'react';
import {
  useOnboardStoreApi,
  useOnboardFormControls,
  useOnboardNavigation,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';
import {
  DEFINITION_DEFAULTS,
  CATEGORY_LABELS,
  CATEGORY_HELP_TEXT,
  CATEGORY_ORDER,
} from '@/components/feature-modules/onboarding/config/definition-defaults';

export interface DefinitionEntry {
  id: string;
  term: string;
  definition: string;
  category: DefinitionCategory;
  isCustom: boolean;
  defaultDefinition?: string;
}

export interface DefinitionsLiveData {
  definitions: DefinitionEntry[];
}

export const DefinitionsStepForm: FC = () => {
  const storeApi = useOnboardStoreApi();
  const { setLiveData, registerFormTrigger, clearFormTrigger } = useOnboardFormControls();
  const { skip } = useOnboardNavigation();

  const [restoredData] = useState(
    () => storeApi.getState().liveData['definitions'] as DefinitionsLiveData | undefined,
  );

  const [definitions, setDefinitions] = useState<DefinitionEntry[]>(() => {
    if (restoredData?.definitions?.length) return restoredData.definitions;

    return DEFINITION_DEFAULTS.map((d) => ({
      id: crypto.randomUUID(),
      term: d.term,
      definition: d.defaultDefinition,
      category: d.category,
      isCustom: false,
      defaultDefinition: d.defaultDefinition,
    }));
  });

  const defaultDefinitionMap = useMemo(
    () => new Map(DEFINITION_DEFAULTS.map((d) => [d.term, d.defaultDefinition])),
    [],
  );

  // Optional step — always valid
  useEffect(() => {
    registerFormTrigger(async () => true);
    return () => clearFormTrigger();
  }, [registerFormTrigger, clearFormTrigger]);

  // Sync liveData
  useEffect(() => {
    setLiveData('definitions', { definitions });
  }, [definitions, setLiveData]);

  const updateDefinition = useCallback((index: number, value: string) => {
    setDefinitions((prev) =>
      prev.map((d, i) => (i === index ? { ...d, definition: value } : d)),
    );
  }, []);

  const updateCustomTerm = useCallback((index: number, value: string) => {
    setDefinitions((prev) =>
      prev.map((d, i) => (i === index ? { ...d, term: value } : d)),
    );
  }, []);

  const updateCustomCategory = useCallback((index: number, value: DefinitionCategory) => {
    setDefinitions((prev) =>
      prev.map((d, i) => (i === index ? { ...d, category: value } : d)),
    );
  }, []);

  const addCustomTerm = useCallback(() => {
    setDefinitions((prev) => [
      ...prev,
      { id: crypto.randomUUID(), term: '', definition: '', category: DefinitionCategory.Custom, isCustom: true },
    ]);
  }, []);

  const removeCustomTerm = useCallback((index: number) => {
    setDefinitions((prev) => prev.filter((_, i) => i !== index));
  }, []);

  const resetToDefault = useCallback(
    (index: number) => {
      setDefinitions((prev) =>
        prev.map((d, i) => {
          if (i !== index) return d;
          const defaultText = d.defaultDefinition ?? defaultDefinitionMap.get(d.term) ?? '';
          return { ...d, definition: defaultText };
        }),
      );
    },
    [defaultDefinitionMap],
  );

  // Group definitions by category
  const grouped = CATEGORY_ORDER.reduce(
    (acc, cat) => {
      const items = definitions
        .map((d, i) => ({ ...d, originalIndex: i }))
        .filter((d) => d.category === cat);
      if (items.length > 0) acc.push({ category: cat, items });
      return acc;
    },
    [] as Array<{
      category: DefinitionCategory;
      items: Array<DefinitionEntry & { originalIndex: number }>;
    }>,
  );

  return (
    <div className="flex flex-col gap-3">
      {grouped.map(({ category, items }) => {
        const customizedCount = items.filter(
          (d) => !d.isCustom && d.defaultDefinition != null && d.definition !== d.defaultDefinition,
        ).length;

        return (
          <Collapsible
            key={category}
            defaultOpen
            className="border-border/60 bg-card/40 group/collapsible rounded-lg border"
          >
            <CollapsibleTrigger className="hover:bg-muted/40 flex w-full items-center justify-between gap-3 rounded-lg px-4 py-3 text-left transition-colors">
              <div className="flex flex-col gap-0.5">
                <div className="flex items-center gap-2">
                  <p className="text-muted-foreground font-mono text-xs font-bold tracking-widest uppercase">
                    {CATEGORY_LABELS[category]}
                  </p>
                  <span className="text-muted-foreground bg-muted/60 rounded-full px-1.5 py-0.5 font-mono text-[10px]">
                    {items.length}
                  </span>
                  {customizedCount > 0 && (
                    <span className="text-foreground bg-primary/10 rounded-full px-1.5 py-0.5 font-mono text-[10px]">
                      {customizedCount} edited
                    </span>
                  )}
                </div>
                <p className="text-muted-foreground text-xs">
                  {CATEGORY_HELP_TEXT[category]}
                </p>
              </div>
              <ChevronDown className="text-muted-foreground size-4 shrink-0 transition-transform group-data-[state=closed]/collapsible:-rotate-90" />
            </CollapsibleTrigger>
            <CollapsibleContent className="data-[state=closed]:animate-collapsible-up data-[state=open]:animate-collapsible-down overflow-hidden">
              <div className="grid grid-cols-1 gap-3 px-4 pt-1 pb-4 sm:grid-cols-2">
                {items.map(({ originalIndex, id, term, definition, isCustom, defaultDefinition }) => {
                  const isModified =
                    !isCustom && defaultDefinition != null && definition !== defaultDefinition;

                  return (
                    <div
                      key={id}
                      className="bg-background flex flex-col gap-2 rounded-lg border p-3"
                    >
                      {isCustom ? (
                        <div className="flex items-center gap-2">
                          <Input
                            value={term}
                            onChange={(e) => updateCustomTerm(originalIndex, e.target.value)}
                            placeholder="Term name"
                            className="text-sm font-semibold"
                          />
                          <Select
                            value={category}
                            onValueChange={(v) =>
                              updateCustomCategory(originalIndex, v as DefinitionCategory)
                            }
                          >
                            <SelectTrigger className="w-32" size="sm">
                              <SelectValue />
                            </SelectTrigger>
                            <SelectContent>
                              {CATEGORY_ORDER.map((cat) => (
                                <SelectItem key={cat} value={cat}>
                                  {CATEGORY_LABELS[cat]}
                                </SelectItem>
                              ))}
                            </SelectContent>
                          </Select>
                          <Button
                            type="button"
                            variant="ghost"
                            size="icon"
                            className="size-8 shrink-0"
                            onClick={() => removeCustomTerm(originalIndex)}
                            aria-label="Remove term"
                          >
                            <X className="size-4" />
                          </Button>
                        </div>
                      ) : (
                        <div className="flex items-center justify-between gap-2">
                          <span className="truncate text-sm font-semibold">{term}</span>
                          <div className="flex shrink-0 items-center gap-1.5">
                            <span
                              className={cn(
                                'text-xs',
                                isModified ? 'text-foreground font-medium' : 'text-muted-foreground',
                              )}
                            >
                              {isModified ? 'Customized' : 'Default'}
                            </span>
                            {isModified && (
                              <Button
                                type="button"
                                variant="ghost"
                                size="icon"
                                className="size-6 shrink-0"
                                onClick={() => resetToDefault(originalIndex)}
                                aria-label="Reset to default"
                              >
                                <RotateCcw className="size-3" />
                              </Button>
                            )}
                          </div>
                        </div>
                      )}
                      <Textarea
                        value={definition}
                        onChange={(e) => updateDefinition(originalIndex, e.target.value)}
                        placeholder={`What does ${term || 'this term'} mean to your business?`}
                        rows={2}
                        className="resize-none text-sm"
                      />
                    </div>
                  );
                })}
              </div>
            </CollapsibleContent>
          </Collapsible>
        );
      })}

      <div className="flex items-center gap-3 pt-1">
        <Button
          type="button"
          variant="ghost"
          size="xs"
          onClick={addCustomTerm}
          className="text-muted-foreground hover:text-foreground gap-1"
        >
          <Plus className="size-3" />
          Add your own term
        </Button>

        <Button
          type="button"
          variant="ghost"
          size="xs"
          onClick={skip}
          className="text-muted-foreground hover:text-foreground ml-auto"
        >
          Skip definitions
        </Button>
      </div>
    </div>
  );
};
