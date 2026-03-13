import { DialogControl } from '@/lib/interfaces/interface';
import { EntityTypeRequestDefinition } from '@/lib/types/entity';
import { Button } from '@riven/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@riven/ui/dialog';
import { AnimatePresence, motion } from 'framer-motion';
import { AlertCircle, AlertTriangle, Loader2, Unlink } from 'lucide-react';
import { FC, useCallback, useMemo, useState } from 'react';

import {
  DeleteAttributeDefinitionRequest,
  DeleteDefinitionImpact,
  DeleteRelationshipDefinitionRequest,
  EntityType,
  EntityTypeDefinition,
  isAttributeDefinition,
  isRelationshipDefinition,
} from '@/lib/types/entity';
import { useDeleteDefinitionMutation } from '../../../../hooks/mutation/type/use-delete-definition-mutation';

interface Props {
  dialog: DialogControl;
  type: EntityType;
  workspaceId: string;
  definition: EntityTypeDefinition;
}

interface DefinitionType {
  isRelationship: boolean;
  isOrigin: boolean;
}

const slideVariants = {
  enter: (direction: number) => ({
    x: direction > 0 ? 200 : -200,
    opacity: 0,
  }),
  center: {
    x: 0,
    opacity: 1,
  },
  exit: (direction: number) => ({
    x: direction > 0 ? -200 : 200,
    opacity: 0,
  }),
};

const slideTransition = {
  type: 'spring' as const,
  stiffness: 400,
  damping: 35,
};

export const DeleteDefinitionModal: FC<Props> = ({
  dialog,
  type: entityType,
  definition,
  workspaceId,
}) => {
  const { open, setOpen: onOpenChange } = dialog;

  const [isDeleting, setIsDeleting] = useState(false);
  const [impact, setImpact] = useState<DeleteDefinitionImpact | null>(null);

  const type: DefinitionType = useMemo(() => {
    if (isRelationshipDefinition(definition.definition)) {
      return {
        isRelationship: true,
        isOrigin: definition.definition.sourceEntityTypeId === entityType.id,
      };
    }
    return {
      isRelationship: false,
      isOrigin: false,
    };
  }, [definition.definition, entityType.id]);

  const handleClose = useCallback(() => {
    onOpenChange(false);
    setImpact(null);
    setIsDeleting(false);
  }, [onOpenChange]);

  const { mutateAsync: deleteDefinition } = useDeleteDefinitionMutation(
    workspaceId,
    (impactData) => {
      setImpact(impactData);
      setIsDeleting(false);
    },
    {
      onSuccess: () => {
        handleClose();
      },
      onError: () => {
        setIsDeleting(false);
      },
    },
  );

  const definitionLabel = isRelationshipDefinition(definition.definition)
    ? definition.definition.name || definition.id
    : isAttributeDefinition(definition.definition)
      ? definition.definition.schema.label || definition.id
      : definition.id;

  const buildRequest = (impactConfirmed: boolean) => {
    if (type.isRelationship) {
      const request: DeleteRelationshipDefinitionRequest = {
        id: definition.id,
        key: entityType.key,
        type: EntityTypeRequestDefinition.DeleteRelationship,
      };
      return { definition: request, impactConfirmed };
    }

    const request: DeleteAttributeDefinitionRequest = {
      id: definition.id,
      key: entityType.key,
      type: EntityTypeRequestDefinition.DeleteSchema,
    };
    return { definition: request, impactConfirmed };
  };

  const handleDelete = async () => {
    if (!definition) return;
    setIsDeleting(true);

    try {
      await deleteDefinition(buildRequest(false));
    } catch {
      // Error handled by mutation hook
    }
  };

  const handleConfirmImpact = async () => {
    if (!definition) return;
    setIsDeleting(true);

    await deleteDefinition(buildRequest(true));
  };

  if (!definition) return null;

  const dialogTitle = type.isRelationship
    ? type.isOrigin
      ? 'Delete Relationship'
      : 'Remove from Relationship'
    : 'Delete Attribute';

  const dialogDescription = type.isRelationship
    ? type.isOrigin
      ? `Are you sure you want to delete the "${definitionLabel}" relationship? This will remove it from this entity type and all target entity types. All associated relationship data will be deleted.`
      : `Are you sure you want to remove ${entityType.name.plural} from the "${definitionLabel}" relationship? This entity type will no longer be a target of this relationship. If this is the last target, the relationship will also be deleted.`
    : `Are you sure you want to delete "${definitionLabel}"? This action cannot be undone.`;

  const step = impact ? 'impact' : 'confirm';
  const hasTransitioned = impact !== null;

  return (
    <Dialog open={open} onOpenChange={handleClose}>
      <DialogContent className="overflow-hidden sm:max-w-lg">
        <AnimatePresence mode="wait" custom={impact ? 1 : -1}>
          {step === 'confirm' && (
            <motion.div
              key="confirm"
              custom={-1}
              variants={slideVariants}
              initial={hasTransitioned ? 'enter' : false}
              animate="center"
              exit="exit"
              transition={slideTransition}
            >
              <DialogHeader>
                <DialogTitle>{dialogTitle}</DialogTitle>
                <DialogDescription>{dialogDescription}</DialogDescription>
              </DialogHeader>

              <div className="space-y-4 py-4">
                {type.isRelationship && type.isOrigin && (
                  <div className="flex items-start gap-2 rounded-md border border-amber-200 bg-amber-50 p-3 dark:border-amber-900 dark:bg-amber-950/20">
                    <AlertCircle className="mt-0.5 size-4 shrink-0 text-amber-600 dark:text-amber-500" />
                    <div className="text-sm text-amber-900 dark:text-amber-200">
                      <p className="mb-1 font-medium">Cascade Deletion</p>
                      <p className="text-amber-800 dark:text-amber-300">
                        This will remove the relationship from this entity type and all target
                        entity types. All associated relationship data will be deleted.
                      </p>
                    </div>
                  </div>
                )}

                {type.isRelationship && !type.isOrigin && (
                  <div className="flex items-start gap-2 rounded-md border border-muted-foreground/20 bg-muted/50 p-3">
                    <AlertCircle className="mt-0.5 size-4 shrink-0 text-muted-foreground" />
                    <div className="text-sm text-muted-foreground">
                      <p className="mb-1 font-medium">Target Removal</p>
                      <p>
                        This entity type will be removed as a target of the &quot;
                        {definitionLabel}&quot; relationship. If this is the last target rule, the
                        entire relationship definition will also be deleted.
                      </p>
                    </div>
                  </div>
                )}

                {!type.isRelationship && (
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
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={handleClose} disabled={isDeleting}>
                  Cancel
                </Button>
                <Button variant="destructive" onClick={handleDelete} disabled={isDeleting}>
                  {isDeleting && <Loader2 className="size-4 animate-spin" />}
                  {isDeleting ? 'Deleting...' : 'Delete'}
                </Button>
              </DialogFooter>
            </motion.div>
          )}

          {step === 'impact' && impact && (
            <motion.div
              key="impact"
              custom={1}
              variants={slideVariants}
              initial="enter"
              animate="center"
              exit="exit"
              transition={slideTransition}
            >
              <DialogHeader>
                <DialogTitle className="flex items-center gap-2">
                  <AlertTriangle className="size-5 text-amber-500" />
                  Impact Confirmation Required
                </DialogTitle>
                <DialogDescription>
                  This action will affect existing data. Please review the impact before proceeding.
                </DialogDescription>
              </DialogHeader>

              <div className="space-y-3 py-4">
                <div className="rounded-md border border-amber-200 bg-amber-50 p-4 dark:border-amber-900 dark:bg-amber-950/20">
                  <p className="mb-3 text-sm font-medium text-amber-900 dark:text-amber-200">
                    Deleting &quot;{impact.definitionName}&quot; will have the following effects:
                  </p>

                  <div className="space-y-2">
                    {impact.impactedLinkCount > 0 && (
                      <div className="flex items-center gap-2 text-sm text-amber-800 dark:text-amber-300">
                        <Unlink className="size-4 shrink-0" />
                        <span>
                          <strong>{impact.impactedLinkCount}</strong>{' '}
                          {impact.impactedLinkCount === 1 ? 'entity link' : 'entity links'} will be
                          removed
                        </span>
                      </div>
                    )}

                    {impact.deletesDefinition && (
                      <div className="flex items-center gap-2 text-sm text-amber-800 dark:text-amber-300">
                        <AlertCircle className="size-4 shrink-0" />
                        <span>
                          The entire relationship definition will be deleted (last target rule being
                          removed)
                        </span>
                      </div>
                    )}
                  </div>
                </div>

                <div className="rounded-md border border-red-200 bg-red-50 p-3 dark:border-red-900 dark:bg-red-950/20">
                  <p className="text-sm font-medium text-red-900 dark:text-red-200">
                    This action cannot be undone.
                  </p>
                </div>
              </div>

              <DialogFooter>
                <Button variant="outline" onClick={handleClose} disabled={isDeleting}>
                  Cancel
                </Button>
                <Button variant="destructive" onClick={handleConfirmImpact} disabled={isDeleting}>
                  {isDeleting && <Loader2 className="size-4 animate-spin" />}
                  {isDeleting ? 'Confirming...' : 'Confirm & Delete'}
                </Button>
              </DialogFooter>
            </motion.div>
          )}
        </AnimatePresence>
      </DialogContent>
    </Dialog>
  );
};
