'use client';

import { Button } from '@riven/ui/button';
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
import { RelationshipFormValues } from '@/components/feature-modules/entity/hooks/form/type/use-relationship-form';
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
  originEntityName: string;
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
  originEntityName,
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
          All entity types are accepted as targets.
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
                    inverseName: originEntityName,
                  })
                }
              >
                Entity Type
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() =>
                  append({
                    ruleType: 'semantic-group',
                    inverseName: originEntityName,
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
