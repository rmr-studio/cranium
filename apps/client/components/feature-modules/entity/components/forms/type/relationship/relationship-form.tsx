'use client';

import { useRelationshipForm } from '@/components/feature-modules/entity/hooks/form/type/use-relationship-form';
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
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
import { Textarea } from '@/components/ui/textarea';
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { DialogControl } from '@/lib/interfaces/interface';
import { EntityType, RelationshipDefinition } from '@/lib/types/entity';
import { FC } from 'react';
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

  const { form, mode, handleSubmit, handleReset, targetRuleFieldArray, cachedRulesRef } =
    useRelationshipForm(workspaceId, type, availableTypes, open, onSave, onCancel, relationship);

  const allowPolymorphic = form.watch('allowPolymorphic');

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-0">
        {/* Section 1: Naming */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Naming
          </h3>

          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Relationship name</FormLabel>
                <FormDescription>How this relationship appears throughout the system</FormDescription>
                <div className="flex items-center gap-3">
                  <FormField
                    control={form.control}
                    name="icon"
                    render={({ field: iconField }) => (
                      <FormItem>
                        <IconSelector
                          onSelect={iconField.onChange}
                          icon={iconField.value}
                          className="size-9 bg-accent/10"
                          displayIconClassName="size-5"
                        />
                      </FormItem>
                    )}
                  />
                  <FormControl>
                    <Input placeholder="E.g. Contacts, Orders, Products" {...field} />
                  </FormControl>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="semanticDefinition"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Definition</FormLabel>
                <FormDescription>
                  Help the system understand this relationship by describing it in plain language
                </FormDescription>
                <FormControl>
                  <Textarea
                    placeholder="Describe the nature of this relationship..."
                    className="resize-none"
                    rows={2}
                    {...field}
                    value={field.value ?? ''}
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </section>

        <div className="border-t" />

        {/* Section 2: Cardinality */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Cardinality
          </h3>
          <p className="text-sm text-muted-foreground">
            Define how many records can be linked on each side of this relationship
          </p>

          <div className="space-y-2">
            <FormField
              control={form.control}
              name="sourceLimit"
              render={({ field }) => (
                <FormItem className="flex items-center gap-2 space-y-0">
                  <span className="text-sm text-muted-foreground">
                    Each{' '}
                    <span className="font-medium text-foreground">{type.name.singular}</span>{' '}
                    can link to
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
                  <span className="text-sm text-muted-foreground">
                    {field.value === 'ONE' ? 'target' : 'targets'}
                  </span>
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
                  <span className="text-sm text-muted-foreground">
                    {field.value === 'ONE' ? type.name.singular : type.name.plural}
                  </span>
                </FormItem>
              )}
            />
          </div>
        </section>

        <div className="border-t" />

        {/* Section 3: Target rules */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
            Target Rules
          </h3>
          <p className="text-sm text-muted-foreground">
            Control which entity types can be linked through this relationship
          </p>

          <TargetRuleList
            availableTypes={availableTypes}
            currentEntityKey={type.key}
            targetRuleFieldArray={targetRuleFieldArray}
            allowPolymorphic={allowPolymorphic}
            cachedRulesRef={cachedRulesRef}
            mode={mode}
            form={form}
            originEntityName={type.name.plural}
          />
        </section>

        {/* Footer: Save/Cancel actions */}
        <footer className="flex justify-end gap-3 border-t px-6 py-4">
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
