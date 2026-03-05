import { Button, Label, Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from '@riven/ui';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { RadioGroup, RadioGroupItem } from '@/components/ui/radio-group';
import { DialogControl } from '@/lib/interfaces/interface';
import { EntityTypeRelationshipType, EntityTypeRequestDefinition } from '@/lib/types/entity';
import { zodResolver } from '@hookform/resolvers/zod';
import { AlertCircle, Loader2 } from 'lucide-react';
import { FC, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import {
  DeleteAttributeDefinitionRequest,
  DeleteRelationshipDefinitionRequest,
  EntityType,
  EntityTypeDefinition,
  isAttributeDefinition,
  isRelationshipDefinition,
  DeleteAction,
} from '@/lib/types/entity';
import { useDeleteDefinitionMutation } from '../../../../hooks/mutation/type/use-delete-definition-mutation';

interface Props {
  dialog: DialogControl;
  type: EntityType;
  definition: EntityTypeDefinition;
}

const DELETE_ACTION_LABELS: Record<DeleteAction, { label: string; description: string }> = {
  [DeleteAction.RemoveBidirectional]: {
    label: 'Remove Bidirectional Link',
    description:
      'Removes the bidirectional link between entities. The relationship will become unidirectional.',
  },
  [DeleteAction.DeleteRelationship]: {
    label: 'Delete Relationship Only',
    description:
      'Removes this relationship definition from the entity type. The relationship data will be deleted.',
  },

  [DeleteAction.RemoveEntityType]: {
    label: 'Remove from Entity Type',
    description:
      'Removes this relationship from the entity type configuration without deleting the relationship data.',
  },
};

const schema = z
  .object({
    action: z.nativeEnum(DeleteAction).optional(),
    type: z.enum(['ORIGIN_RELATIONSHIP', 'REFERENCE_RELATIONSHIP', 'SCHEMA']),
  })
  .refine(
    (data) => {
      if (data.type === 'REFERENCE_RELATIONSHIP') {
        return data.action !== undefined;
      }
      return true;
    },
    {
      message: 'Delete action is required for reference relationships',
    },
  );

type DeleteDefinitionFormValues = z.infer<typeof schema>;
type DefinitionType = 'ORIGIN_RELATIONSHIP' | 'REFERENCE_RELATIONSHIP' | 'SCHEMA';

export const DeleteDefinitionModal: FC<Props> = ({ dialog, type: entityType, definition }) => {
  const { open, setOpen: onOpenChange } = dialog;
  const { data: workspace } = useWorkspace();
  const [isDeleting, setIsDeleting] = useState(false);

  const form = useForm<DeleteDefinitionFormValues>({
    resolver: zodResolver(schema),
  });

  useEffect(() => {
    if (!isRelationshipDefinition(definition.definition)) {
      form.setValue('type', 'SCHEMA');
      return;
    }
    form.setValue(
      'type',
      definition.definition.relationshipType === EntityTypeRelationshipType.Reference
        ? 'REFERENCE_RELATIONSHIP'
        : 'ORIGIN_RELATIONSHIP',
    );
  }, [definition, form]);

  const type: DefinitionType = form.watch('type');
  const action: DeleteAction | undefined = form.watch('action');

  const { mutateAsync: deleteDefinition } = useDeleteDefinitionMutation(workspace?.id || '', {
    onMutate: () => {
      setIsDeleting(true);
    },
    onSuccess: () => {
      setIsDeleting(false);
      onOpenChange(false);
    },
    onError: () => {
      setIsDeleting(false);
    },
  });

  const onSubmit = async (values: DeleteDefinitionFormValues) => {
    if (!definition || !workspace) return;

    const isRelationship = isRelationshipDefinition(definition.definition);

    if (isRelationship) {
      const isReference = values.type === 'REFERENCE_RELATIONSHIP';

      if (isReference && !values.action) return;

      const request: DeleteRelationshipDefinitionRequest = {
        id: definition.id,
        key: entityType.key,
        type: EntityTypeRequestDefinition.DeleteRelationship,
        deleteAction:
          isReference && values.action ? values.action : DeleteAction.DeleteRelationship,
      };

      await deleteDefinition({
        definition: request,
      });

      return;
    }

    const request: DeleteAttributeDefinitionRequest = {
      id: definition.id,
      key: entityType.key,
      type: EntityTypeRequestDefinition.DeleteSchema,
    };

    await deleteDefinition({
      definition: request,
    });
  };

  if (!workspace || !definition) return null;

  const definitionLabel = isRelationshipDefinition(definition.definition)
    ? definition.definition.name || definition.id
    : isAttributeDefinition(definition.definition)
      ? definition.definition.schema.label || definition.id
      : definition.id;

  const canDelete = type !== 'REFERENCE_RELATIONSHIP' || action !== undefined;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[500px]">
        <DialogHeader>
          <DialogTitle>Delete {type === 'SCHEMA' ? 'Attribute' : 'Relationship'}</DialogTitle>
          <DialogDescription>
            Are you sure you want to delete &quot;{definitionLabel}&quot;? This action cannot be
            undone.
          </DialogDescription>
        </DialogHeader>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 py-4">
            {type === 'REFERENCE_RELATIONSHIP' && (
              <>
                <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 dark:border-amber-900 dark:bg-amber-950/20">
                  <AlertCircle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-500" />
                  <div className="text-sm text-amber-900 dark:text-amber-200">
                    <p className="mb-1 font-medium">Relationship Deletion</p>
                    <p className="text-amber-800 dark:text-amber-300">
                      Please select how you want to handle this relationship deletion.
                    </p>
                  </div>
                </div>

                <FormField
                  control={form.control}
                  name="action"
                  render={({ field }) => (
                    <FormItem className="space-y-3">
                      <FormLabel>Deletion Action</FormLabel>
                      <FormControl>
                        <RadioGroup
                          onValueChange={field.onChange}
                          value={field.value}
                          className="space-y-3"
                        >
                          {Object.entries(DELETE_ACTION_LABELS).map(([actionKey, info]) => (
                            <FormItem
                              key={actionKey}
                              className="flex cursor-pointer items-start space-y-0 space-x-3 rounded-md border p-4 transition-colors hover:bg-accent/50"
                              onClick={() => field.onChange(actionKey)}
                            >
                              <FormControl>
                                <RadioGroupItem value={actionKey} className="mt-0.5" />
                              </FormControl>
                              <div className="flex-1 space-y-1">
                                <Label
                                  htmlFor={actionKey}
                                  className="cursor-pointer text-sm font-medium"
                                >
                                  {info.label}
                                </Label>
                                <p className="text-sm text-muted-foreground">{info.description}</p>
                              </div>
                            </FormItem>
                          ))}
                        </RadioGroup>
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </>
            )}

            {type === 'ORIGIN_RELATIONSHIP' && (
              <div className="flex items-start gap-2 rounded-md border border-green-200 bg-green-50 p-3 dark:border-green-900 dark:bg-green-950/20">
                <AlertCircle className="mt-0.5 size-4 shrink-0 text-green-600 dark:text-green-500" />
                <div className="text-sm text-green-900 dark:text-green-200">
                  <p className="mb-1 font-medium">Info</p>
                  <p className="text-green-800 dark:text-green-300">
                    Deleting this relationship will remove it from the entity type, and from all
                    other entity types that currently hold a two way relationship with it. All
                    associated relationship data will be deleted.
                  </p>
                </div>
              </div>
            )}

            {type === 'SCHEMA' && (
              <div className="flex items-start gap-2 rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/20">
                <AlertCircle className="mt-0.5 size-4 shrink-0 text-red-600 dark:text-red-500" />
                <div className="text-sm text-red-900 dark:text-red-200">
                  <p className="mb-1 font-medium">Warning</p>
                  <p className="text-red-800 dark:text-red-300">
                    Deleting this attribute will remove it from all entities of this type. This
                    action cannot be undone.
                  </p>
                </div>
              </div>
            )}
          </form>
        </Form>

        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)} disabled={isDeleting}>
            Cancel
          </Button>
          <Button
            variant="destructive"
            onClick={form.handleSubmit(onSubmit)}
            disabled={!canDelete || isDeleting}
          >
            {isDeleting && <Loader2 className="size-4 animate-spin" />}
            {isDeleting ? 'Deleting...' : 'Delete'}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
};
