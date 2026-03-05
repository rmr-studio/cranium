import { useEntityTypeRelationshipForm } from '@/components/feature-modules/entity/hooks/form/type/use-relationship-form';
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { Button, Input } from '@riven/ui';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Switch } from '@/components/ui/switch';
import { DialogControl } from '@/lib/interfaces/interface';
import {
  EntityRelationshipDefinition,
  EntityType,
  EntityTypeRelationshipType,
  RelationshipLimit,
} from '@/lib/types/entity';
import { cn } from '@riven/utils';
import { FC, useCallback, useEffect, useMemo, useState } from 'react';
import { useRelationshipOverlapDetection } from '../../../../hooks/use-relationship-overlap-detection';
import {
  getInverseCardinality,
  processCardinalityToLimits,
} from '../../../../util/relationship.util';
import { RelationshipOverlapAlert } from '../../relationship-overlap-alert';
import { EntityTypeMultiSelect } from './entity-type-multi-select';
import { RelationshipLink } from './relationship-links';

interface Props {
  workspaceId: string;
  type: EntityType;
  relationship?: EntityRelationshipDefinition;
  dialog: DialogControl;
}

export const RelationshipAttributeForm: FC<Props> = ({
  type,
  relationship,
  dialog,
  workspaceId,
}) => {
  const { open, setOpen: onOpenChange } = dialog;
  const onSave = () => {
    onOpenChange(false);
  };

  const onCancel = () => {
    onOpenChange(false);
  };

  const { data: availableTypes = [] } = useEntityTypes(workspaceId);
  const { form, mode, handleSubmit, handleReset } = useEntityTypeRelationshipForm(
    workspaceId,
    type,
    open,
    onSave,
    onCancel,
    relationship,
  );

  const isReference = relationship?.relationshipType === EntityTypeRelationshipType.Reference;
  const selectedEntityTypeKeys = form.watch('entityTypeKeys');
  const bidirectional = form.watch('bidirectional');
  const allowPolymorphic = form.watch('allowPolymorphic');

  // Determine the current entity key for self-reference identification
  const currentEntityKey = type?.key;

  // Overlap detection state and logic
  const [dismissedOverlaps, setDismissedOverlaps] = useState<Set<string>>(new Set());

  // Detect overlaps when target entity selection changes
  const overlapDetection = useRelationshipOverlapDetection(
    type?.key,
    selectedEntityTypeKeys,
    allowPolymorphic,
    availableTypes,
  );

  const createFromSuggestion = (relationship: EntityRelationshipDefinition) => {
    if (!relationship.inverseName) return;
    const inverse = getInverseCardinality(relationship.cardinality);
    const { source, target } = processCardinalityToLimits(inverse);

    form.setValue('name', relationship.inverseName);
    form.setValue('allowPolymorphic', false);
    form.setValue('relationshipType', EntityTypeRelationshipType.Reference);
    form.setValue('entityTypeKeys', [relationship.sourceEntityTypeKey]);
    form.setValue('sourceEntityTypeKey', relationship.sourceEntityTypeKey);
    form.setValue('sourceRelationsLimit', source);
    form.setValue('targetRelationsLimit', target);
    form.setValue('originRelationshipId', relationship.id);

    form.handleSubmit(handleSubmit)();
  };

  const cardinalityToggleEntityName = useMemo(() => {
    if (selectedEntityTypeKeys && selectedEntityTypeKeys.length === 1) {
      const targetType = availableTypes.find((et) => et.key === selectedEntityTypeKeys[0]);
      return targetType ? targetType.name.singular : 'entity';
    }
    return 'Entity';
  }, [selectedEntityTypeKeys, availableTypes]);

  // Filter out dismissed overlaps
  const activeOverlaps = useMemo(() => {
    return overlapDetection.overlaps.filter((overlap) => {
      const overlapId = `${overlap.targetEntityKey}-${overlap.existingRelationship.id}`;
      return !dismissedOverlaps.has(overlapId);
    });
  }, [overlapDetection.overlaps, dismissedOverlaps]);

  // Handler for dismissing an overlap alert
  const handleDismissOverlap = useCallback(
    (index: number) => {
      const overlap = activeOverlaps[index];
      const overlapId = `${overlap.targetEntityKey}-${overlap.existingRelationship.id}`;
      setDismissedOverlaps((prev) => new Set([...prev, overlapId]));
    },
    [activeOverlaps],
  );

  // Handler for navigating to target entity to edit relationship
  // todo go back and fix this
  const handleNavigateToTarget = useCallback(
    (targetEntityKey: string, relationshipKey: string) => {
      // Store suggestion in sessionStorage for target entity editor
      sessionStorage.setItem(
        'relationship-suggestion',
        JSON.stringify({
          sourceEntityKey: type?.key,
          relationshipKey,
          action: 'add-to-bidirectional',
          timestamp: Date.now(),
        }),
      );

      // Navigate to target entity editor
      const workspaceId = window.location.pathname.split('/')[2];
      window.location.href = `/workspace/${workspaceId}/entity/type/${targetEntityKey}`;
    },
    [type?.key],
  );

  // Reset dismissed overlaps when target entity selection changes
  useEffect(() => {
    setDismissedOverlaps(new Set());
  }, [selectedEntityTypeKeys]);

  const bidirectionalDisabled =
    (!selectedEntityTypeKeys || selectedEntityTypeKeys.length === 0) && !allowPolymorphic;
  const bidirectionalFormDisabled = !bidirectional || bidirectionalDisabled;

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)}>
        <section className="flex gap-4">
          <div className="flex w-auto grow flex-col space-y-6">
            {/* Overlap Alert Banner */}
            {activeOverlaps.length > 0 && (
              <RelationshipOverlapAlert
                overlaps={activeOverlaps}
                sourceEntityKey={type?.key || ''}
                onDismiss={handleDismissOverlap}
                onNavigateToTarget={handleNavigateToTarget}
              />
            )}
            <div className="rounded-lg border p-6">
              <div className="flex w-full flex-col items-start gap-6 lg:flex-row">
                <FormField
                  control={form.control}
                  name="name"
                  render={({ field }) => (
                    <FormItem className="w-full">
                      <FormLabel>Relationship Name</FormLabel>
                      <FormControl>
                        <Input placeholder="E.g. Person" {...field} />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />

                <FormField
                  control={form.control}
                  name="entityTypeKeys"
                  render={({ field, fieldState }) => (
                    <FormItem
                      className={cn('w-full', isReference && 'cursor-not-allowed opacity-50')}
                    >
                      <FormLabel>Related to</FormLabel>
                      <FormControl>
                        <EntityTypeMultiSelect
                          disabled={isReference}
                          availableTypes={availableTypes}
                          selectedKeys={selectedEntityTypeKeys || []}
                          allowPolymorphic={allowPolymorphic || false}
                          hasError={!!fieldState.error}
                          currentEntityKey={currentEntityKey}
                          onSelectionChange={(keys, allowPoly) => {
                            form.setValue('entityTypeKeys', keys);
                            form.setValue('allowPolymorphic', allowPoly);
                          }}
                        />
                      </FormControl>
                      <FormDescription className="ml-1 text-xs">
                        {isReference
                          ? 'You cannot change which entity this reference points to.'
                          : 'Select one or more entity types, or allow all entities'}
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>

              <FormField
                control={form.control}
                name="sourceRelationsLimit"
                render={({ field }) => (
                  <FormItem
                    className={cn(isReference && 'cursor-not-allowed opacity-50', 'mx-0.5 w-fit')}
                  >
                    <FormLabel>Limit</FormLabel>
                    <div className="flex items-center gap-4 py-1">
                      <span
                        className={cn(
                          'text-sm font-medium transition-colors',
                          field.value === RelationshipLimit.SINGULAR
                            ? 'text-foreground'
                            : 'text-muted-foreground',
                        )}
                      >
                        1 {cardinalityToggleEntityName}
                      </span>
                      <FormControl>
                        <Switch
                          disabled={isReference}
                          checked={field.value === RelationshipLimit.MANY}
                          onCheckedChange={(checked) => {
                            field.onChange(
                              checked ? RelationshipLimit.MANY : RelationshipLimit.SINGULAR,
                            );
                          }}
                        />
                      </FormControl>
                      <span
                        className={cn(
                          'text-sm font-medium transition-colors',
                          field.value === RelationshipLimit.MANY
                            ? 'text-foreground'
                            : 'text-muted-foreground',
                        )}
                      >
                        Unlimited
                      </span>
                    </div>
                    <FormDescription className="text-xs">
                      {/* // Todo. Add a link back to the original definition */}
                      {isReference
                        ? 'You can change cardinality in the original relationship definition.'
                        : 'Controls how many entities this relationship can point to'}
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            {!isReference && (
              <div className="space-y-4 rounded-lg border p-4">
                <FormField
                  control={form.control}
                  name="bidirectional"
                  render={({ field }) => (
                    <FormItem className="flex items-center justify-between space-y-0">
                      <div className="space-y-1">
                        <FormLabel className={cn(bidirectionalDisabled && 'text-primary/60')}>
                          Two Way Relationship
                        </FormLabel>
                        <FormDescription className="text-xs">
                          Manage this relationship from the target entity as well
                        </FormDescription>
                      </div>
                      <FormControl>
                        <Switch
                          disabled={bidirectionalDisabled}
                          checked={field.value}
                          onCheckedChange={field.onChange}
                        />
                      </FormControl>
                    </FormItem>
                  )}
                />
                <div className="flex w-full flex-col items-start gap-6 lg:flex-row">
                  <FormField
                    control={form.control}
                    name="inverseName"
                    render={({ field }) => (
                      <FormItem className="w-full">
                        <FormLabel className={cn(bidirectionalFormDisabled && 'text-primary/60')}>
                          Default Associated Attribute Name
                        </FormLabel>
                        <FormControl>
                          <Input
                            disabled={bidirectionalFormDisabled}
                            placeholder="E.g. Company"
                            {...field}
                          />
                        </FormControl>
                        <FormDescription className="text-xs">
                          The default name of this relationship on the target entity side
                        </FormDescription>
                        <FormMessage />
                      </FormItem>
                    )}
                  />

                  <FormItem className="w-full">
                    <FormLabel className={cn(bidirectionalFormDisabled && 'text-primary/60')}>
                      Bidirectional Entity Types
                    </FormLabel>
                    <FormControl>
                      <EntityTypeMultiSelect
                        disabled={bidirectionalFormDisabled}
                        allowSelectAll={false}
                        availableTypes={
                          allowPolymorphic
                            ? availableTypes
                            : availableTypes.filter((et) =>
                                selectedEntityTypeKeys?.includes(et.key),
                              )
                        }
                        selectedKeys={form.watch('bidirectionalEntityTypeKeys') || []}
                        allowPolymorphic={false}
                        currentEntityKey={currentEntityKey}
                        onSelectionChange={(keys) => {
                          form.setValue('bidirectionalEntityTypeKeys', keys);
                        }}
                      />
                    </FormControl>
                    <FormDescription className="text-xs">
                      Select which entity types should receive the two way relationship
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                </div>
                <FormField
                  control={form.control}
                  name="targetRelationsLimit"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel className={cn(bidirectionalFormDisabled && 'text-primary/60')}>
                        Limit
                      </FormLabel>
                      <div className="flex items-center gap-4 py-1">
                        <span
                          className={cn(
                            'text-sm font-medium transition-colors',
                            field.value === RelationshipLimit.SINGULAR
                              ? 'text-foreground'
                              : 'text-muted-foreground',
                            bidirectionalFormDisabled && 'text-primary/60',
                          )}
                        >
                          1 {type.name.singular}
                        </span>
                        <FormControl>
                          <Switch
                            disabled={bidirectionalFormDisabled}
                            checked={field.value === RelationshipLimit.MANY}
                            onCheckedChange={(checked) => {
                              field.onChange(
                                checked ? RelationshipLimit.MANY : RelationshipLimit.SINGULAR,
                              );
                            }}
                          />
                        </FormControl>
                        <span
                          className={cn(
                            'text-sm font-medium transition-colors',
                            field.value === RelationshipLimit.MANY
                              ? 'text-foreground'
                              : 'text-muted-foreground',
                            bidirectionalFormDisabled && 'text-primary/60',
                          )}
                        >
                          Unlimited
                        </span>
                      </div>
                      <FormDescription className="text-xs">
                        Controls how many entities the target side of this relationship can point to
                      </FormDescription>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
            )}
            {/* // TODO: Only allow setting required on RELATIONSHIP entity types */}
            {/* <div className="w-full flex justify-end">
                <FormField
                    control={form.control}
                    name="required"
                    render={({ field }) => (
                        <FormItem className="flex items-center justify-between space-y-0 w-1/3 rounded-lg border p-4">
                            <FormLabel>Required</FormLabel>
                            <FormControl>
                                <Switch checked={field.value} onCheckedChange={field.onChange} />
                            </FormControl>
                        </FormItem>
                    )}
                />
            </div> */}
          </div>
          {mode === 'create' && <RelationshipLink type={type} onCreate={createFromSuggestion} />}
        </section>
        <footer className="flex justify-end space-x-4 border-t pt-4">
          <Button type="button" onClick={handleReset} variant={'destructive'}>
            Cancel
          </Button>
          <Button type="submit">{relationship ? 'Update Relationship' : 'Add Relationship'}</Button>
        </footer>
      </form>
    </Form>
  );
};
