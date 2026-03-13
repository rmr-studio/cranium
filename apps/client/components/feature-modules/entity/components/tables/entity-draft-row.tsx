'use client';

import { EntityPropertyType, EntityType, SystemRelationshipType } from '@/lib/types/entity';
import { TableCell, TableRow } from '@riven/ui/table';
import { Row } from '@tanstack/react-table';
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

    const relationship = type.relationships?.find((r) => r.id === id);
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

    // Filter out system relationship columns (e.g. ConnectedEntities)
    const filteredColumns = entityType.columns.filter((col) => {
      if (col.type !== EntityPropertyType.Relationship) return true;
      const rel = entityType.relationships?.find((r) => r.id === col.key);
      return !rel || rel.systemType !== SystemRelationshipType.ConnectedEntities;
    });

    return filteredColumns.map((item, index) => {
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

  return (
    <>
      <TableRow className="relative border-dashed bg-muted/30 hover:bg-muted/40">
        {/* Empty action column cell */}
        <TableCell className="px-0">
          <div className="flex h-full items-center justify-center">
            <div className="size-1.5 rounded-full bg-primary/30" />
          </div>
        </TableCell>

        {/* Ordered cells (attributes and relationships) */}
        {orderedCells}

        {/* Empty cell to match endOfHeaderContent column */}
        <TableCell />
      </TableRow>

      {/* Keyboard hint row */}
      <TableRow className="border-none hover:bg-transparent">
        <TableCell
          colSpan={orderedCells.length + 1}
          className="px-3 py-1.5"
        >
          <div className="flex items-center gap-3 text-xs text-muted-foreground/50">
            <span className="flex items-center gap-1">
              <kbd className="rounded border border-border/40 bg-muted/50 px-1 py-0.5 font-mono text-[10px]">
                Enter
              </kbd>
              <span>save</span>
            </span>
            <span className="flex items-center gap-1">
              <kbd className="rounded border border-border/40 bg-muted/50 px-1 py-0.5 font-mono text-[10px]">
                Esc
              </kbd>
              <span>cancel</span>
            </span>
            {isSubmitting && (
              <span className="text-primary/60">Saving...</span>
            )}
            {hasErrors && (
              <span className="text-destructive/60">Fix errors to save</span>
            )}
          </div>
        </TableCell>
      </TableRow>
    </>
  );
};
