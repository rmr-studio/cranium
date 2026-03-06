'use client';

import { RelationshipFormValues } from '@/components/feature-modules/entity/hooks/form/type/use-relationship-form';
import { Button } from '@/components/ui/button';
import {
  Command,
  CommandEmpty,
  CommandGroup,
  CommandInput,
  CommandItem,
  CommandList,
} from '@/components/ui/command';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@/components/ui/popover';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { EntityRelationshipCardinality, EntityType, SemanticGroup } from '@/lib/types/entity';
import { cn } from '@/lib/util/utils';
import { Check, ChevronsUpDown, MoreHorizontal, Repeat, X } from 'lucide-react';
import { FC, useState } from 'react';
import { useFormContext } from 'react-hook-form';

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

// ---- Entity type single-select ----

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
        onOpenAutoFocus={(e: React.FocusEvent<HTMLDivElement>) => e.preventDefault()}
        onEscapeKeyDown={(e: React.KeyboardEvent<HTMLDivElement>) => {
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

  // Keys already used in OTHER rules (to prevent duplicates)
  const allRules = form.watch('targetRules');
  const usedKeys = allRules
    .filter((_, i) => i !== index)
    .map((r) => r.targetEntityTypeKey)
    .filter((k): k is string => !!k);

  return (
    <div className="flex items-center gap-2 rounded-lg border p-2">
      {/* Selector - takes most of the width */}
      <div className="min-w-0 flex-1">
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
                      className={cn('w-full', fieldState.error && 'border-destructive')}
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
                <FormLabel>Inverse name *</FormLabel>
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
