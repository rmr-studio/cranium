# Relationship Form Redesign — Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Simplify the relationship form by replacing abstract cardinality toggles with plain-language sentences, flattening target rules into compact rows, and hiding rarely-used constraints behind a popover.

**Architecture:** Pure UI refactor — no schema, hook, or API changes. The form values and submit logic remain identical. Three component files change: `relationship-form.tsx` (cardinality section), `target-rule-item.tsx` (flatten to row + popover), `target-rule-list.tsx` (receives polymorphic toggle).

**Tech Stack:** React 19, react-hook-form, shadcn/ui (ToggleGroup, Popover, Tooltip, Switch, Select), Tailwind 4, Lucide icons.

---

## Task 1: Rewrite cardinality section in relationship-form.tsx

**Files:**
- Modify: `components/feature-modules/entity/components/forms/type/relationship/relationship-form.tsx:112-208`

**Step 1: Replace cardinality toggles with plain-language sentence layout**

Remove the entire `<div className="flex flex-wrap items-end gap-6">` block (lines 113-208) containing `sourceLimit`, `targetLimit`, and `allowPolymorphic` fields.

Replace with:

```tsx
{/* Section 3: Cardinality as plain language */}
<div className="space-y-3">
  <FormLabel className="text-sm">Cardinality</FormLabel>
  <div className="space-y-2">
    <FormField
      control={form.control}
      name="sourceLimit"
      render={({ field }) => (
        <FormItem className="flex items-center gap-2 space-y-0">
          <span className="text-sm text-muted-foreground">
            Each <span className="font-medium text-foreground">{type.name.singular}</span> can
            link to
          </span>
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={field.value}
            onValueChange={(val) => {
              if (val) field.onChange(val);
            }}
          >
            <ToggleGroupItem value="ONE" className="text-xs px-2.5">
              one
            </ToggleGroupItem>
            <ToggleGroupItem value="UNLIMITED" className="text-xs px-2.5">
              many
            </ToggleGroupItem>
          </ToggleGroup>
          <span className="text-sm text-muted-foreground">target</span>
        </FormItem>
      )}
    />

    <FormField
      control={form.control}
      name="targetLimit"
      render={({ field }) => (
        <FormItem className="flex items-center gap-2 space-y-0">
          <span className="text-sm text-muted-foreground">Each target can link to</span>
          <ToggleGroup
            type="single"
            variant="outline"
            size="sm"
            value={field.value}
            onValueChange={(val) => {
              if (val) field.onChange(val);
            }}
          >
            <ToggleGroupItem value="ONE" className="text-xs px-2.5">
              one
            </ToggleGroupItem>
            <ToggleGroupItem value="UNLIMITED" className="text-xs px-2.5">
              many
            </ToggleGroupItem>
          </ToggleGroup>
          <span className="text-sm text-muted-foreground">{type.name.singular}</span>
        </FormItem>
      )}
    />
  </div>
</div>
```

**Step 2: Update imports in relationship-form.tsx**

Add:
```tsx
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
```

Remove (no longer used in this file):
```tsx
import { Switch } from '@/components/ui/switch';
import { cn } from '@/lib/util/utils';
```

**Step 3: Move allowPolymorphic toggle to TargetRuleList**

Remove the `allowPolymorphic` FormField from `relationship-form.tsx` entirely. It will live in `target-rule-list.tsx` instead.

Update the `<TargetRuleList>` call to pass the new props:

```tsx
<TargetRuleList
  availableTypes={availableTypes}
  currentEntityKey={type.key}
  targetRuleFieldArray={targetRuleFieldArray}
  allowPolymorphic={allowPolymorphic}
  cachedRulesRef={cachedRulesRef}
  mode={mode}
  form={form}
/>
```

Remove `disabled={allowPolymorphic}` prop — the component now manages this internally.

**Step 4: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -40`
Expected: May show errors in `target-rule-list.tsx` since we changed its props — that's expected, we fix it in Task 2.

**Step 5: Commit**

```bash
git add components/feature-modules/entity/components/forms/type/relationship/relationship-form.tsx
git commit -m "refactor(relationship-form): replace cardinality toggles with plain-language sentences"
```

---

## Task 2: Update TargetRuleList to own the polymorphic toggle

**Files:**
- Modify: `components/feature-modules/entity/components/forms/type/relationship/target-rule-list.tsx`

**Step 1: Update props interface and add polymorphic toggle**

Replace the entire file content:

```tsx
'use client';

import { Button } from '@/components/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@/components/ui/dropdown-menu';
import { FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form';
import { Switch } from '@/components/ui/switch';
import { EntityType } from '@/lib/types/entity';
import { Plus } from 'lucide-react';
import { FC } from 'react';
import { UseFieldArrayReturn, UseFormReturn } from 'react-hook-form';
import { RelationshipFormValues } from '../../../hooks/form/type/use-relationship-form';
import { TargetRuleItem } from './target-rule-item';

// ---- Props ----

interface TargetRuleListProps {
  availableTypes: EntityType[];
  currentEntityKey: string;
  targetRuleFieldArray: UseFieldArrayReturn<RelationshipFormValues, 'targetRules'>;
  allowPolymorphic: boolean;
  cachedRulesRef: React.MutableRefObject<RelationshipFormValues['targetRules']>;
  mode: 'create' | 'edit';
  form: UseFormReturn<RelationshipFormValues>;
}

// ---- Component ----

export const TargetRuleList: FC<TargetRuleListProps> = ({
  availableTypes,
  currentEntityKey,
  targetRuleFieldArray,
  allowPolymorphic,
  cachedRulesRef,
  mode,
  form,
}) => {
  const { fields, append, remove } = targetRuleFieldArray;
  const ruleValues = form.watch('targetRules');

  return (
    <div className="space-y-4">
      {/* Polymorphic toggle */}
      <FormField
        control={form.control}
        name="allowPolymorphic"
        render={({ field }) => (
          <FormItem className="flex items-center justify-between space-y-0">
            <FormLabel className="text-sm font-normal">Allow all entity types</FormLabel>
            <FormControl>
              <Switch
                checked={field.value}
                onCheckedChange={(checked) => {
                  if (checked) {
                    cachedRulesRef.current = form.getValues('targetRules');
                    targetRuleFieldArray.remove();
                  } else {
                    if (mode === 'create' && cachedRulesRef.current.length > 0) {
                      form.setValue('targetRules', cachedRulesRef.current);
                    }
                  }
                  field.onChange(checked);
                }}
              />
            </FormControl>
          </FormItem>
        )}
      />

      {/* Rules or polymorphic message */}
      {allowPolymorphic ? (
        <p className="text-sm text-muted-foreground">
          All entity types are accepted as targets. Add rules below to restrict.
        </p>
      ) : (
        <>
          {fields.length === 0 && (
            <p className="text-sm text-muted-foreground">
              No target rules defined. Add a rule to restrict which entity types can be targets.
            </p>
          )}

          {fields.map((field, index) => (
            <TargetRuleItem
              key={field.id}
              index={index}
              onRemove={() => remove(index)}
              availableTypes={availableTypes}
              currentEntityKey={currentEntityKey}
              isExistingRule={!!ruleValues[index]?.id}
            />
          ))}

          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button variant="outline" size="sm" type="button">
                <Plus className="size-4 mr-2" />
                Add rule
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="start">
              <DropdownMenuItem
                onSelect={() =>
                  append({
                    ruleType: 'entity-type',
                    targetEntityTypeKey: '',
                    inverseVisible: true,
                  })
                }
              >
                Entity Type
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() =>
                  append({
                    ruleType: 'semantic-group',
                    inverseVisible: true,
                  })
                }
              >
                Semantic Group
              </DropdownMenuItem>
            </DropdownMenuContent>
          </DropdownMenu>
        </>
      )}
    </div>
  );
};
```

**Step 2: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -40`
Expected: May still error on `target-rule-item.tsx` — fixed in Task 3.

**Step 3: Commit**

```bash
git add components/feature-modules/entity/components/forms/type/relationship/target-rule-list.tsx
git commit -m "refactor(target-rule-list): own polymorphic toggle, simplify props"
```

---

## Task 3: Flatten TargetRuleItem to compact row with overflow popover

**Files:**
- Modify: `components/feature-modules/entity/components/forms/type/relationship/target-rule-item.tsx`

**Step 1: Rewrite the component**

Replace the entire file. Key changes:
- Remove `Collapsible` / `CollapsibleContent` / `CollapsibleTrigger` imports
- Add `Popover` / `PopoverContent` / `PopoverTrigger` (already imported, reuse)
- Add `Tooltip` / `TooltipContent` / `TooltipTrigger` / `TooltipProvider`
- Inline the "inverse visible" as an eye icon toggle button
- Move cardinality override + inverse name into a popover behind `MoreHorizontal` icon

```tsx
'use client';

import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from '@/components/ui/tooltip';
import { EntityRelationshipCardinality, EntityType, SemanticGroup } from '@/lib/types/entity';
import { cn } from '@/lib/util/utils';
import { Check, ChevronsUpDown, Eye, EyeOff, MoreHorizontal, Repeat, X } from 'lucide-react';
import { FC, useState } from 'react';
import { useFormContext } from 'react-hook-form';
import { RelationshipFormValues } from '../../../hooks/form/type/use-relationship-form';

// ---- Human-friendly labels ----

const SEMANTIC_GROUP_LABELS: Record<SemanticGroup, string> = {
  [SemanticGroup.Customer]: 'Customer',
  [SemanticGroup.Product]: 'Product',
  [SemanticGroup.Transaction]: 'Transaction',
  [SemanticGroup.Communication]: 'Communication',
  [SemanticGroup.Support]: 'Support',
  [SemanticGroup.Financial]: 'Financial',
  [SemanticGroup.Operational]: 'Operational',
  [SemanticGroup.Custom]: 'Custom',
  [SemanticGroup.Uncategorized]: 'Uncategorized',
};

const CARDINALITY_LABELS: Record<EntityRelationshipCardinality, string> = {
  [EntityRelationshipCardinality.OneToOne]: 'One to One',
  [EntityRelationshipCardinality.OneToMany]: 'One to Many',
  [EntityRelationshipCardinality.ManyToOne]: 'Many to One',
  [EntityRelationshipCardinality.ManyToMany]: 'Many to Many',
};

// ---- Props ----

interface TargetRuleItemProps {
  index: number;
  onRemove: () => void;
  availableTypes: EntityType[];
  currentEntityKey: string;
  isExistingRule: boolean;
}

// ---- Entity type single-select (unchanged) ----

interface EntityTypeSingleSelectProps {
  value: string | undefined;
  onChange: (value: string) => void;
  availableTypes: EntityType[];
  disabledKeys: string[];
  currentEntityKey: string;
  disabled?: boolean;
  hasError?: boolean;
}

const EntityTypeSingleSelect: FC<EntityTypeSingleSelectProps> = ({
  value,
  onChange,
  availableTypes,
  disabledKeys,
  currentEntityKey,
  disabled = false,
  hasError = false,
}) => {
  const [open, setOpen] = useState(false);

  const selected = availableTypes.find((et) => et.key === value);

  const getDisplayText = () => {
    if (!selected) return 'Select entity type...';
    const isSelf = selected.key === currentEntityKey;
    return isSelf ? 'This entity' : selected.name.plural;
  };

  return (
    <Popover open={open} onOpenChange={setOpen} modal>
      <PopoverTrigger asChild>
        <Button
          type="button"
          variant="outline"
          role="combobox"
          aria-expanded={open}
          disabled={disabled}
          className={cn(
            'w-full justify-between',
            hasError && 'border-destructive focus-visible:ring-destructive',
          )}
        >
          {selected ? (
            <div className="flex items-center gap-2">
              <div className="flex h-5 w-5 items-center justify-center rounded bg-primary/10">
                {selected.key === currentEntityKey ? (
                  <Repeat className="h-3 w-3 text-primary" />
                ) : (
                  <span className="text-xs">{selected.name.plural?.charAt(0) ?? '?'}</span>
                )}
              </div>
              <span className="truncate">{getDisplayText()}</span>
            </div>
          ) : (
            <span className="text-muted-foreground">{getDisplayText()}</span>
          )}
          <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
        </Button>
      </PopoverTrigger>
      <PopoverContent
        className="w-[280px] p-0"
        align="start"
        onOpenAutoFocus={(e) => e.preventDefault()}
        onEscapeKeyDown={(e) => {
          e.stopPropagation();
          setOpen(false);
        }}
      >
        <Command>
          <CommandInput placeholder="Search entity types..." />
          <CommandList>
            <CommandEmpty>No entity type found.</CommandEmpty>
            <CommandGroup>
              {availableTypes.map((entityType) => {
                const isSelf = entityType.key === currentEntityKey;
                const isDisabled =
                  disabledKeys.includes(entityType.key) && entityType.key !== value;
                const displayName = isSelf ? 'This entity' : entityType.name.plural;

                return (
                  <CommandItem
                    key={entityType.key}
                    value={displayName}
                    disabled={isDisabled}
                    onSelect={() => {
                      onChange(entityType.key);
                      setOpen(false);
                    }}
                    className={cn('cursor-pointer', isDisabled && 'cursor-not-allowed opacity-50')}
                  >
                    <div className="flex items-center gap-2">
                      <div className="flex h-5 w-5 items-center justify-center rounded bg-primary/10">
                        {isSelf ? (
                          <Repeat className="h-3 w-3 text-primary" />
                        ) : (
                          <span className="text-xs">{displayName?.charAt(0) ?? '?'}</span>
                        )}
                      </div>
                      <span>{displayName}</span>
                    </div>
                    {value === entityType.key && <Check className="ml-auto h-4 w-4 text-primary" />}
                  </CommandItem>
                );
              })}
            </CommandGroup>
          </CommandList>
        </Command>
      </PopoverContent>
    </Popover>
  );
};

// ---- Main component ----

export const TargetRuleItem: FC<TargetRuleItemProps> = ({
  index,
  onRemove,
  availableTypes,
  currentEntityKey,
  isExistingRule,
}) => {
  const form = useFormContext<RelationshipFormValues>();
  const ruleType = form.watch(`targetRules.${index}.ruleType`);
  const inverseVisible = form.watch(`targetRules.${index}.inverseVisible`);

  // Keys already used in OTHER rules (to prevent duplicates)
  const allRules = form.watch('targetRules');
  const usedKeys = allRules
    .filter((_, i) => i !== index)
    .map((r) => r.targetEntityTypeKey)
    .filter((k): k is string => !!k);

  return (
    <div className="flex items-center gap-2 rounded-lg border p-2">
      {/* Selector — takes most of the width */}
      <div className="flex-1 min-w-0">
        {ruleType === 'entity-type' ? (
          <FormField
            control={form.control}
            name={`targetRules.${index}.targetEntityTypeKey`}
            render={({ field, fieldState }) => (
              <FormItem className="space-y-0">
                <FormControl>
                  <EntityTypeSingleSelect
                    value={field.value}
                    onChange={field.onChange}
                    availableTypes={availableTypes}
                    disabledKeys={usedKeys}
                    currentEntityKey={currentEntityKey}
                    disabled={isExistingRule}
                    hasError={!!fieldState.error}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        ) : (
          <FormField
            control={form.control}
            name={`targetRules.${index}.semanticTypeConstraint`}
            render={({ field, fieldState }) => (
              <FormItem className="space-y-0">
                <FormControl>
                  <Select
                    value={field.value ?? ''}
                    onValueChange={(val) =>
                      field.onChange(val ? (val as SemanticGroup) : undefined)
                    }
                  >
                    <SelectTrigger
                      className={cn(
                        'w-full',
                        fieldState.error && 'border-destructive',
                      )}
                    >
                      <SelectValue placeholder="Select semantic group..." />
                    </SelectTrigger>
                    <SelectContent>
                      {Object.values(SemanticGroup).map((group) => (
                        <SelectItem key={group} value={group}>
                          {SEMANTIC_GROUP_LABELS[group]}
                        </SelectItem>
                      ))}
                    </SelectContent>
                  </Select>
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        )}
      </div>

      {/* Inline: inverse visible toggle */}
      <TooltipProvider delayDuration={300}>
        <Tooltip>
          <TooltipTrigger asChild>
            <Button
              type="button"
              variant="ghost"
              size="xs"
              className={cn(
                'shrink-0',
                inverseVisible ? 'text-foreground' : 'text-muted-foreground',
              )}
              onClick={() =>
                form.setValue(`targetRules.${index}.inverseVisible`, !inverseVisible)
              }
            >
              {inverseVisible ? <Eye className="size-4" /> : <EyeOff className="size-4" />}
            </Button>
          </TooltipTrigger>
          <TooltipContent side="top">
            <p>{inverseVisible ? 'Visible on target entity' : 'Hidden on target entity'}</p>
          </TooltipContent>
        </Tooltip>
      </TooltipProvider>

      {/* Overflow menu: rarely-used constraints */}
      <Popover>
        <PopoverTrigger asChild>
          <Button
            type="button"
            variant="ghost"
            size="xs"
            className="shrink-0 text-muted-foreground"
          >
            <MoreHorizontal className="size-4" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-72 space-y-4" align="end">
          <FormField
            control={form.control}
            name={`targetRules.${index}.cardinalityOverride`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Cardinality override</FormLabel>
                <Select
                  value={field.value ?? '__DEFAULT__'}
                  onValueChange={(val) =>
                    field.onChange(
                      val === '__DEFAULT__' ? undefined : (val as EntityRelationshipCardinality),
                    )
                  }
                >
                  <FormControl>
                    <SelectTrigger className="w-full">
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    <SelectItem value="__DEFAULT__">Default</SelectItem>
                    {Object.values(EntityRelationshipCardinality).map((card) => (
                      <SelectItem key={card} value={card}>
                        {CARDINALITY_LABELS[card]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name={`targetRules.${index}.inverseName`}
            render={({ field }) => (
              <FormItem>
                <FormLabel>Inverse name</FormLabel>
                <FormControl>
                  <Input placeholder="E.g. Company" {...field} value={field.value ?? ''} />
                </FormControl>
                <FormDescription className="text-xs">
                  Name when viewed from the target entity
                </FormDescription>
                <FormMessage />
              </FormItem>
            )}
          />
        </PopoverContent>
      </Popover>

      {/* Remove button */}
      <Button
        type="button"
        variant="ghost"
        size="xs"
        onClick={onRemove}
        className="shrink-0 text-muted-foreground hover:text-destructive"
      >
        <X className="size-4" />
      </Button>
    </div>
  );
};
```

**Step 2: Verify build**

Run: `npx tsc --noEmit --pretty 2>&1 | head -40`
Expected: PASS (all three files now aligned)

**Step 3: Commit**

```bash
git add components/feature-modules/entity/components/forms/type/relationship/target-rule-item.tsx
git commit -m "refactor(target-rule-item): flatten to compact row with overflow popover"
```

---

## Task 4: Final verification

**Step 1: Type check**

Run: `npx tsc --noEmit --pretty`
Expected: PASS

**Step 2: Lint**

Run: `npm run lint`
Expected: PASS

**Step 3: Build**

Run: `npm run build`
Expected: PASS

**Step 4: Visual check**

Open the relationship form in the browser. Verify:
- Cardinality reads as two natural sentences with segmented toggles
- Target rules display as compact rows
- Eye icon toggles inverse visibility with tooltip
- `...` button opens popover with cardinality override + inverse name
- "Allow all entity types" toggle works and hides/shows rules
- Form submits correctly in both create and edit modes
