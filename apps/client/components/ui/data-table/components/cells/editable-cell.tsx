'use client';

import { Cell } from '@tanstack/react-table';
import { Loader2 } from 'lucide-react';
import { useCallback, useEffect, useRef } from 'react';
import { UseFormReturn } from 'react-hook-form';
import { toast } from 'sonner';
import { useDataTableStore } from '@/components/ui/data-table/data-table-provider';
import { isEditableColumn } from '@/components/ui/data-table/data-table.types';
import type { ColumnEditConfig } from '@/components/ui/data-table/data-table.types';

export interface EditableCellProps<TData, TCellValue> {
  cell: Cell<TData, TCellValue>;
  onSave: (value: TCellValue) => Promise<void>;
  onCancel: () => void;
  onFocusNext?: () => void;
  onFocusPrev?: () => void;
}

/**
 * EditableCell - Thin wrapper component
 *
 * Guards against non-editable columns and delegates to EditableCellInner
 * which safely calls all hooks unconditionally.
 */
export function EditableCell<TData, TCellValue, TValue = TCellValue>({
  cell,
  onSave,
  onCancel,
  onFocusNext,
  onFocusPrev,
}: EditableCellProps<TData, TCellValue>) {
  const meta = cell.column.columnDef.meta;

  // Guard: Column must have edit config
  if (!isEditableColumn(meta)) {
    return <div className="text-sm text-destructive">Column not editable</div>;
  }

  const editConfig = meta.edit as unknown as ColumnEditConfig<TData, TCellValue, TValue>;

  return (
    <EditableCellInner
      cell={cell}
      editConfig={editConfig}
      onSave={onSave}
      onCancel={onCancel}
      onFocusNext={onFocusNext}
      onFocusPrev={onFocusPrev}
    />
  );
}

/**
 * EditableCellInner - Manages form state and keyboard navigation
 *
 * Extracted so hooks are never called conditionally (after the
 * isEditableColumn guard in the parent).
 */
function EditableCellInner<TData, TCellValue, TValue = TCellValue>({
  cell,
  editConfig,
  onSave,
  onCancel,
  onFocusNext,
  onFocusPrev,
}: EditableCellProps<TData, TCellValue> & {
  editConfig: ColumnEditConfig<TData, TCellValue, TValue>;
}) {
  const form = editConfig.createFormInstance(cell as any) as UseFormReturn<{ value: TValue }>;

  const isSaving = useDataTableStore<TData, boolean>((state) => state.isSaving);
  const registerCommitCallback = useDataTableStore<TData, (callback: (() => void) | null) => void>(
    (state) => state.registerCommitCallback,
  );

  const handleSaveRef = useRef<() => Promise<boolean>>(() => Promise.resolve(false));
  const isSavingRef = useRef(false);

  /**
   * Validates the form and saves if the value has changed.
   * Returns true on success (saved or no change), false on validation failure or save error.
   */
  const handleSave = useCallback(async (): Promise<boolean> => {
    // Guard against concurrent saves (e.g. popover blur + click-outside both firing)
    if (isSavingRef.current) return false;
    isSavingRef.current = true;

    try {
      // Validate the form — use trigger() for a fresh validation result
      const valid = await form.trigger();

      if (!valid) {
        // Keep editor open so the user sees validation errors
        return false;
      }

      // Get the new value from the form
      const { value: newEditValue } = form.getValues();

      // Get the original cell value
      const originalCellValue: TCellValue = cell.getValue();

      // Parse the original cell value to edit format for comparison
      const parseValue = editConfig.parseValue ?? ((val: TCellValue) => val as unknown as TValue);
      const originalEditValue: TValue = parseValue(originalCellValue);

      // Check if the value has changed
      const isEqual =
        editConfig.isEqual ?? ((a: TValue, b: TValue) => JSON.stringify(a) === JSON.stringify(b));
      const hasChanged = !isEqual(originalEditValue, newEditValue);

      // Only save if the value has changed
      if (!hasChanged) {
        onCancel(); // Close editor without saving
        return true;
      }

      // Format the value back to cell format for saving
      const formatValue =
        editConfig.formatValue ?? ((val: TValue) => val as unknown as TCellValue);
      const newCellValue: TCellValue = formatValue(newEditValue);

      try {
        await onSave(newCellValue);
        return true;
      } catch (error) {
        const message = error instanceof Error ? error.message : 'Failed to save';
        toast.error(message);
        return false;
      }
    } finally {
      isSavingRef.current = false;
    }
  }, [onSave, onCancel, cell, editConfig, form]);

  handleSaveRef.current = handleSave;

  // Register commit callback once on mount
  useEffect(() => {
    registerCommitCallback(() => {
      handleSaveRef.current();
    });
    return () => registerCommitCallback(null);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Keyboard shortcuts wrapper
  const handleKeyDown = async (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      e.stopPropagation();
      handleSave();
    } else if (e.key === 'Escape') {
      e.preventDefault();
      e.stopPropagation();
      onCancel();
    } else if (e.key === 'Tab') {
      e.preventDefault();
      e.stopPropagation();
      const success = await handleSave();
      if (success) {
        e.shiftKey ? onFocusPrev?.() : onFocusNext?.();
      }
    }
  };

  // Render: Delegate to column's custom render function
  return (
    <div className="relative -my-2 flex w-full items-center" onKeyDown={handleKeyDown}>
      {editConfig.render({
        cell: cell as any,
        form: form as any,
        value: form.watch('value' as any) as TValue,
        onSave: handleSave,
        onCancel,
        onFocusNext,
        onFocusPrev,
        isSaving,
      } as any)}

      {/* Saving overlay */}
      {isSaving && (
        <div className="absolute inset-0 flex items-center justify-center rounded bg-background/50">
          <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />
        </div>
      )}
    </div>
  );
}
