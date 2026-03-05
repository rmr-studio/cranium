'use client';

import { Button } from '@riven/ui/button';
import { TableCell, TableRow } from '@riven/ui/table';
import { EntityPropertyType, EntityType } from '@/lib/types/entity';
import { cn } from '@riven/utils';
import { Row } from '@tanstack/react-table';
import { Check, X } from 'lucide-react';
import { FC, ReactNode, useCallback, useEffect, useMemo, useState } from 'react';
import { useFormState } from 'react-hook-form';
import { toast } from 'sonner';
import { useEntityDraft } from '../../context/entity-provider';
import { EntityFieldCell } from '../forms/instance/entity-field-cell';
import { DraftEntityRelationshipPicker } from '../forms/instance/relationship/draft-entity-picker';
import { EntityRow } from './entity-table-utils';

export interface EntityDraftRowProps {
  entityType: EntityType;
  row: Row<EntityRow>;
}

export const EntityDraftRow: FC<EntityDraftRowProps> = ({ entityType, row }) => {
  const { form, resetDraft, submitDraft } = useEntityDraft();
  // Check if form is valid

  const { errors } = useFormState({
    control: form.control,
  });

  const hasErrors = Object.keys(errors).length > 0;

  const [isSubmitting, setIsSubmitting] = useState(false);

  const handleSubmit = useCallback(async () => {
    setIsSubmitting(true);
    try {
      await submitDraft();
      toast.success('Entity created successfully!');
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Failed to create entity';
      toast.error(message);
    } finally {
      setIsSubmitting(false);
    }
  }, [submitDraft]);

  const handleReset = useCallback(() => {
    resetDraft();
  }, [resetDraft]);

  // Keyboard event listeners for Enter (submit) and Escape (cancel)
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      // Enter key: submit the draft
      if (event.key === 'Enter' && !event.shiftKey && !event.ctrlKey && !event.metaKey) {
        // Don't submit if there are errors or already submitting
        if (!hasErrors && !isSubmitting) {
          event.preventDefault();
          handleSubmit();
        }
      }

      // Escape key: cancel and reset the draft
      if (event.key === 'Escape') {
        if (!isSubmitting) {
          event.preventDefault();
          handleReset();
        }
      }
    };

    // Add event listener
    window.addEventListener('keydown', handleKeyDown);

    // Cleanup on unmount
    return () => {
      window.removeEventListener('keydown', handleKeyDown);
    };
  }, [hasErrors, isSubmitting, handleSubmit, handleReset]); // Re-attach listener when these dependencies change

  // Build a map of column IDs to their sizes from the row's cells
  const columnSizeMap = useMemo(() => {
    const map = new Map<string, number>();
    row.getVisibleCells().forEach((cell) => {
      map.set(cell.column.id, cell.column.getSize());
    });
    return map;
  }, [row]);

  const getElement = (
    id: string,
    type: EntityType,
    property: EntityPropertyType,
    isFirstCell: boolean,
  ): ReactNode | null => {
    if (property === EntityPropertyType.Attribute) {
      const schema = entityType.schema.properties?.[id];
      if (!schema) return null;
      return <EntityFieldCell attributeId={id} schema={schema} autoFocus={isFirstCell} />;
    }

    const relationship = entityType.relationships?.find((r) => r.id === id);
    if (property === EntityPropertyType.Relationship && relationship) {
      return <DraftEntityRelationshipPicker relationship={relationship} />;
    }

    return null;
  };

// Build ordered cells based on entityType.columns
  const orderedCells = useMemo(() => {
    const columnCount = entityType.columns ? entityType.columns.length : 0;
    if (columnCount === 0) return [];
    if (!entityType.columns) return [];

    return entityType.columns.map((item, index) => {
      const { key: id, type } = item;
      const isFirstCell = index === 0;

      // Create element with autoFocus on first cell
      const element = getElement(id, entityType, type, isFirstCell);
      if (!element) return null;

      const width = columnSizeMap.get(id);
      return (
        <TableCell
          key={id}
          className="relative border-l border-l-accent/40 p-2 first:border-l-transparent"
          style={{
            width: width ? `${width}px` : undefined,
            maxWidth: width ? `${width}px` : undefined,
          }}
        >
          {element}
        </TableCell>
      );
    });
  }, [entityType, columnSizeMap, isSubmitting, hasErrors]);

  const submitDisabled = isSubmitting || hasErrors;

  return (
    <>
      <TableRow className="relative border-dashed bg-muted/30 hover:bg-muted/40">
        {/* // Action buttons cell (This is rendered inside the action column instead of the drag handle etc) */}
        <TableCell className="justify-start px-0">
          <div className="z-30 flex flex-wrap">
            <Button
              size="sm"
              variant="ghost"
              className="h-8 w-8 p-0 text-destructive hover:bg-destructive/10 hover:text-destructive"
              onClick={handleReset}
              disabled={isSubmitting}
              title="Cancel and discard draft"
            >
              <X className="h-4 w-4" />
            </Button>
            <Button
              size="sm"
              variant="ghost"
              className={cn(
                `h-8 w-8 p-0 text-green-600 hover:bg-green-50 hover:text-green-700`,
                submitDisabled && 'text-primary/40',
              )}
              onClick={handleSubmit}
              disabled={submitDisabled}
            >
              <Check className="h-4 w-4" />
            </Button>
          </div>
        </TableCell>

        {/* Ordered cells (attributes and relationships) */}
        {orderedCells}

        {/* Action buttons cell */}
      </TableRow>
    </>
  );
};
