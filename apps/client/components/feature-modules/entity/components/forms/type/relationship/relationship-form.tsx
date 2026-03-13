'use client';

import { useRelationshipForm } from '@/components/feature-modules/entity/hooks/form/type/use-relationship-form';
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { Badge } from '@riven/ui/badge';
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
import { ToggleGroup, ToggleGroupItem } from '@/components/ui/toggle-group';
import { DialogControl } from '@/lib/interfaces/interface';
import { EntityType, RelationshipDefinition } from '@/lib/types/entity';
import { ArrowUpRight, Info } from 'lucide-react';
import Link from 'next/link';
import { FC, useMemo } from 'react';
import { TargetRuleList } from './target-rule-list';

// ---- Props ----

interface RelationshipFormProps {
  workspaceId: string;
  type: EntityType;
  relationship?: RelationshipDefinition;
  dialog: DialogControl;
  isTargetSide?: boolean;
  sourceEntityTypeKey?: string;
}

// ---- Component ----

export const RelationshipForm: FC<RelationshipFormProps> = ({
  workspaceId,
  type,
  relationship,
  dialog,
  isTargetSide,
  sourceEntityTypeKey,
}) => {
  const { open, setOpen } = dialog;

  const onSave = () => setOpen(false);
  const onCancel = () => setOpen(false);

  const { data: availableTypes = [] } = useEntityTypes(workspaceId);

  const { form, mode, handleSubmit, handleReset, targetRuleFieldArray, cachedRulesRef } =
    useRelationshipForm(workspaceId, type, availableTypes, open, onSave, onCancel, relationship);

  const matchingTargetRuleIndex = useMemo(() => {
    if (!isTargetSide || !relationship) return -1;
    return relationship.targetRules.findIndex((rule) => {
      const targetType = availableTypes.find((et) => et.id === rule.targetEntityTypeId);
      return targetType?.id === type.id;
    });
  }, [isTargetSide, relationship, availableTypes, type.id]);

  if (isTargetSide && relationship) {
    const sourceType = availableTypes.find((et) => et.id === relationship.sourceEntityTypeId);
    const sourcePluralName = sourceType?.name.plural ?? 'the source type';

    const sourceLimit = form.getValues('sourceLimit');
    const targetLimit = form.getValues('targetLimit');

    const targetRuleTypes = relationship.targetRules
      .map((rule) => availableTypes.find((et) => et.id === rule.targetEntityTypeId))
      .filter(Boolean);

    return (
      <Form {...form}>
        <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-0">
          {/* Info Banner */}
          <section className="mx-6 mt-5 mb-0 flex items-start gap-3 rounded-md border border-muted bg-muted/40 px-4 py-3">
            <Info className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
            <div className="space-y-1 text-sm">
              <p className="text-muted-foreground">
                This relationship is defined on{' '}
                <span className="font-medium text-foreground">{sourcePluralName}</span>.
              </p>
              <Link
                href={`/dashboard/workspace/${workspaceId}/entity/${sourceEntityTypeKey}/settings?tab=attributes&edit=${relationship.id}`}
                onClick={() => dialog.setOpen(false)}
                className="inline-flex items-center gap-1 text-sm font-medium text-primary hover:underline"
              >
                Edit source relationship
                <ArrowUpRight className="size-3.5" />
              </Link>
            </div>
          </section>

          {/* Read-Only Overview */}
          <section className="space-y-4 px-6 py-5">
            <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Relationship Overview
            </h3>

            <div className="space-y-3">
              <div>
                <p className="text-xs font-medium text-muted-foreground">Name</p>
                <p className="text-sm">{relationship.name}</p>
              </div>

              <div>
                <p className="text-xs font-medium text-muted-foreground">Cardinality</p>
                <div className="mt-1 space-y-1 text-sm text-muted-foreground">
                  <p>
                    Each{' '}
                    <span className="font-medium text-foreground">{type.name.singular}</span> can
                    link to{' '}
                    <span className="font-medium text-foreground">
                      {sourceLimit === 'ONE' ? 'one' : 'many'}
                    </span>{' '}
                    {sourceLimit === 'ONE' ? 'target' : 'targets'}
                  </p>
                  <p>
                    Each target can link to{' '}
                    <span className="font-medium text-foreground">
                      {targetLimit === 'ONE' ? 'one' : 'many'}
                    </span>{' '}
                    {targetLimit === 'ONE' ? type.name.singular : type.name.plural}
                  </p>
                </div>
              </div>

              {targetRuleTypes.length > 0 && (
                <div>
                  <p className="text-xs font-medium text-muted-foreground">Target types</p>
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {targetRuleTypes.map((et) => (
                      <Badge key={et!.id} variant="outline" className="text-xs">
                        {et!.name.plural}
                      </Badge>
                    ))}
                  </div>
                </div>
              )}
            </div>
          </section>

          <div className="border-t" />

          {/* Editable Section */}
          <section className="space-y-4 px-6 py-5">
            <h3 className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Display on this Entity
            </h3>

            {matchingTargetRuleIndex >= 0 && (
              <FormField
                control={form.control}
                name={`targetRules.${matchingTargetRuleIndex}.inverseName`}
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Inverse name</FormLabel>
                    <FormDescription>
                      How this relationship appears when viewed from {type.name.plural}
                    </FormDescription>
                    <FormControl>
                      <Input {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            )}
          </section>

          {/* Footer */}
          <footer className="flex justify-end gap-3 border-t px-6 py-4">
            <Button type="button" onClick={handleReset} variant="outline">
              Cancel
            </Button>
            <Button type="submit">Save</Button>
          </footer>
        </form>
      </Form>
    );
  }

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
            allowPolymorphic={false}
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
