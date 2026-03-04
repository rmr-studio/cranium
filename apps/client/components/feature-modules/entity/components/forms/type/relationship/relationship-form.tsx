'use client';

import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { Button } from '@/components/ui/button';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { IconSelector } from '@/components/ui/icon/icon-selector';
import { Input } from '@/components/ui/input';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { DialogControl } from '@/lib/interfaces/interface';
import { EntityType, RelationshipDefinition } from '@/lib/types/entity';
import { cn } from '@/lib/util/utils';
import { FC } from 'react';
import {
  useRelationshipForm,
} from '../../../hooks/form/type/use-relationship-form';
import { TargetRuleList } from './target-rule-list';

// ---- Props ----

interface RelationshipFormProps {
  workspaceId: string;
  type: EntityType;
  relationship?: RelationshipDefinition;
  dialog: DialogControl;
}

// ---- Component ----

export const RelationshipForm: FC<RelationshipFormProps> = ({
  workspaceId,
  type,
  relationship,
  dialog,
}) => {
  const { open, setOpen } = dialog;

  const onSave = () => setOpen(false);
  const onCancel = () => setOpen(false);

  const { data: availableTypes = [] } = useEntityTypes(workspaceId);

  const {
    form,
    mode,
    handleSubmit,
    handleReset,
    targetRuleFieldArray,
    cachedRulesRef,
  } = useRelationshipForm(workspaceId, type, availableTypes, open, onSave, onCancel, relationship);

  const allowPolymorphic = form.watch('allowPolymorphic');

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-6">
        {/* Section 1: Icon + Name inline */}
        <div className="flex items-start gap-4">
          <FormField
            control={form.control}
            name="icon"
            render={({ field }) => (
              <FormItem>
                <IconSelector
                  onSelect={field.onChange}
                  icon={field.value}
                  className="size-12 bg-accent/10"
                  displayIconClassName="size-8"
                />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem className="flex-1">
                <FormLabel>Relationship Name</FormLabel>
                <FormControl>
                  <Input placeholder="E.g. Contacts, Orders, Products" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Section 2: Semantic definition textarea */}
        <FormField
          control={form.control}
          name="semanticDefinition"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Definition</FormLabel>
              <FormControl>
                <Textarea
                  placeholder="Describe the nature of this relationship..."
                  className="resize-none"
                  rows={2}
                  {...field}
                  value={field.value ?? ''}
                />
              </FormControl>
              <FormDescription>
                Help the system understand this relationship by describing it in plain language
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />

        {/* Section 3: Cardinality toggles + polymorphic toggle */}
        <div className="flex flex-wrap items-end gap-6">
          <FormField
            control={form.control}
            name="sourceLimit"
            render={({ field }) => (
              <FormItem className="w-fit">
                <FormLabel>Source cardinality</FormLabel>
                <div className="flex items-center gap-3 py-1">
                  <span
                    className={cn(
                      'text-sm font-medium',
                      field.value === 'ONE' ? 'text-foreground' : 'text-muted-foreground',
                    )}
                  >
                    1
                  </span>
                  <FormControl>
                    <Switch
                      checked={field.value === 'UNLIMITED'}
                      onCheckedChange={(checked) => field.onChange(checked ? 'UNLIMITED' : 'ONE')}
                    />
                  </FormControl>
                  <span
                    className={cn(
                      'text-sm font-medium',
                      field.value === 'UNLIMITED' ? 'text-foreground' : 'text-muted-foreground',
                    )}
                  >
                    Unlimited
                  </span>
                </div>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="targetLimit"
            render={({ field }) => (
              <FormItem className="w-fit">
                <FormLabel>Target cardinality</FormLabel>
                <div className="flex items-center gap-3 py-1">
                  <span
                    className={cn(
                      'text-sm font-medium',
                      field.value === 'ONE' ? 'text-foreground' : 'text-muted-foreground',
                    )}
                  >
                    1
                  </span>
                  <FormControl>
                    <Switch
                      checked={field.value === 'UNLIMITED'}
                      onCheckedChange={(checked) => field.onChange(checked ? 'UNLIMITED' : 'ONE')}
                    />
                  </FormControl>
                  <span
                    className={cn(
                      'text-sm font-medium',
                      field.value === 'UNLIMITED' ? 'text-foreground' : 'text-muted-foreground',
                    )}
                  >
                    Unlimited
                  </span>
                </div>
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="allowPolymorphic"
            render={({ field }) => (
              <FormItem className="flex items-center gap-3 space-y-0">
                <FormLabel>Allow all entity types</FormLabel>
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
                        // Edit mode: start fresh with empty rules per CONTEXT.md
                      }
                      field.onChange(checked);
                    }}
                  />
                </FormControl>
              </FormItem>
            )}
          />
        </div>

        {/* Section 4: Target rules list */}
        <TargetRuleList
          availableTypes={availableTypes}
          currentEntityKey={type.key}
          targetRuleFieldArray={targetRuleFieldArray}
          disabled={allowPolymorphic}
        />

        {/* Section 5: Save/Cancel actions */}
        <footer className="flex justify-end gap-3 border-t pt-4">
          <Button type="button" onClick={handleReset} variant="outline">
            Cancel
          </Button>
          <Button type="submit">
            {mode === 'edit' ? 'Update Relationship' : 'Add Relationship'}
          </Button>
        </footer>
      </form>
    </Form>
  );
};
