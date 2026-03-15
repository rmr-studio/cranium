import { EntityType } from '@/lib/types/entity';
import { useCallback } from 'react';
import { UseFormReturn } from 'react-hook-form';
import { EntityTypeFormValues } from '@/components/feature-modules/entity/context/configuration-provider';

export function useEntityColumnConfig(
  form: UseFormReturn<EntityTypeFormValues>,
  entityType: EntityType,
) {
  const handleColumnResize = useCallback(
    (columnSizing: Record<string, number>) => {
      const current = form.getValues('columnConfiguration');
      const updatedOverrides = { ...current.overrides };
      Object.entries(columnSizing).forEach(([key, width]) => {
        updatedOverrides[key] = { ...updatedOverrides[key], width };
      });
      form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
    },
    [form],
  );

  const handleHideColumn = useCallback(
    (columnId: string) => {
      const current = form.getValues('columnConfiguration');
      form.setValue(
        'columnConfiguration.overrides',
        {
          ...current.overrides,
          [columnId]: { ...current.overrides[columnId], visible: false },
        },
        { shouldDirty: true },
      );
    },
    [form],
  );

  const handleToggleVisibility = useCallback(
    (columnId: string) => {
      if (columnId === entityType.identifierKey) return;
      const current = form.getValues('columnConfiguration');
      const currentVisible = current.overrides[columnId]?.visible !== false;
      form.setValue(
        'columnConfiguration.overrides',
        {
          ...current.overrides,
          [columnId]: { ...current.overrides[columnId], visible: !currentVisible },
        },
        { shouldDirty: true },
      );
    },
    [form, entityType.identifierKey],
  );

  const handleReorder = useCallback(
    (newOrder: string[]) => {
      form.setValue('columnConfiguration.order', newOrder, { shouldDirty: true });
    },
    [form],
  );

  const handleShowAll = useCallback(() => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    Object.keys(updatedOverrides).forEach((key) => {
      updatedOverrides[key] = { ...updatedOverrides[key], visible: true };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  }, [form]);

  const handleHideAll = useCallback(() => {
    const current = form.getValues('columnConfiguration');
    const updatedOverrides = { ...current.overrides };
    // Iterate over all entity type columns (not just existing overrides)
    // to ensure columns without prior overrides also get hidden
    const allColumnKeys = entityType.columns?.map((col) => col.key) ?? Object.keys(updatedOverrides);
    allColumnKeys.forEach((key) => {
      if (key === entityType.identifierKey) return;
      updatedOverrides[key] = { ...updatedOverrides[key], visible: false };
    });
    form.setValue('columnConfiguration.overrides', updatedOverrides, { shouldDirty: true });
  }, [form, entityType.identifierKey, entityType.columns]);

  return {
    handleColumnResize,
    handleHideColumn,
    handleToggleVisibility,
    handleReorder,
    handleShowAll,
    handleHideAll,
  };
}
