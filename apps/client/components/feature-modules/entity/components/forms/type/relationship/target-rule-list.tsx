'use client';

import { Button } from '@cranium/ui/button';
import { EntityType } from '@/lib/types/entity';
import { Plus } from 'lucide-react';
import { FC } from 'react';
import { UseFieldArrayReturn, UseFormReturn } from 'react-hook-form';
import { RelationshipFormValues } from '@/components/feature-modules/entity/hooks/form/type/use-relationship-form';
import { TargetRuleItem } from './target-rule-item';

// ---- Props ----

interface TargetRuleListProps {
  availableTypes: EntityType[];
  currentEntityKey: string;
  targetRuleFieldArray: UseFieldArrayReturn<RelationshipFormValues, 'targetRules'>;
  /**
   * @deprecated Currently unused — polymorphic relationships are temporarily disabled.
   * The allowPolymorphic toggle and semantic group target rules will be re-enabled
   * in a future iteration. See also: cachedRulesRef, mode (polymorphic caching logic).
   */
  allowPolymorphic: boolean;
  cachedRulesRef: React.MutableRefObject<RelationshipFormValues['targetRules']>;
  mode: 'create' | 'edit';
  form: UseFormReturn<RelationshipFormValues>;
  originEntityName: string;
}

// ---- Component ----

export const TargetRuleList: FC<TargetRuleListProps> = ({
  availableTypes,
  currentEntityKey,
  targetRuleFieldArray,
  form,
  originEntityName,
}) => {
  const { fields, append, remove } = targetRuleFieldArray;
  const ruleValues = form.watch('targetRules');
  const targetRulesError = form.formState.errors.targetRules?.root?.message;

  // TEMPORARILY DISABLED: Polymorphic toggle and semantic group target rules.
  // When re-enabling, restore:
  //   - The "Allow all entity types" Switch (FormField for allowPolymorphic)
  //   - The DropdownMenu for "Add rule" with "Entity Type" / "Semantic Group" options
  //   - The polymorphic message ("All entity types are accepted as targets.")
  //   - cachedRulesRef caching logic on toggle
  // See git history for the original implementation.

  return (
    <div className="space-y-4">
      {fields.length === 0 && (
        <p className={targetRulesError ? 'text-sm text-destructive' : 'text-sm text-muted-foreground'}>
          {targetRulesError ?? 'No target rules defined. Add a rule to restrict which entity types can be targets.'}
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

      <Button
        variant="outline"
        size="sm"
        type="button"
        onClick={() =>
          append({
            ruleType: 'entity-type',
            targetEntityTypeKey: '',
            inverseName: originEntityName,
          })
        }
      >
        <Plus className="size-4 mr-2" />
        Add rule
      </Button>
    </div>
  );
};
