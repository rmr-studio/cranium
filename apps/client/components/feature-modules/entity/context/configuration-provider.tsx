'use client';

import { EntityType } from '@/lib/types/entity';
import { zodResolver } from '@hookform/resolvers/zod';
import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react';
import { useForm, useFormState } from 'react-hook-form';
import { isUUID } from 'validator';
import { z } from 'zod';
import { useStore } from 'zustand';
import { baseEntityTypeFormSchema } from '../hooks/form/type/use-new-type-form';
import { useSaveEntityTypeConfiguration } from '../hooks/mutation/type/use-save-configuration-mutation';
import {
  createEntityTypeConfigStore,
  EntityTypeConfigStore,
} from '../stores/type/configuration.store';

type EntityTypeConfigStoreApi = ReturnType<typeof createEntityTypeConfigStore>;

const EntityTypeConfigContext = createContext<EntityTypeConfigStoreApi | undefined>(undefined);

export interface EntityTypeConfigurationProviderProps {
  children: ReactNode;
  workspaceId: string;
  entityType: EntityType;
}

// Zod schema for entity type form
const entityTypeFormSchema = z
  .object({
    identifierKey: z.string().min(1, 'Identifier key is required').refine(isUUID),
    columnConfiguration: z.object({
      order: z.array(z.string()),
      overrides: z.record(
        z.string(),
        z.object({
          width: z.number().min(150).max(1000).optional(),
          visible: z.boolean().optional(),
        }),
      ),
    }),
  })
  .extend(baseEntityTypeFormSchema.shape);

export type EntityTypeFormValues = z.infer<typeof entityTypeFormSchema>;

export const EntityTypeConfigurationProvider = ({
  children,
  workspaceId,
  entityType,
}: EntityTypeConfigurationProviderProps) => {
  const storeRef = useRef<EntityTypeConfigStoreApi | null>(null);

  // Create form instance
  const form = useForm<EntityTypeFormValues>({
    resolver: zodResolver(entityTypeFormSchema),
    defaultValues: {
      key: entityType.key,
      singularName: entityType.name.singular,
      pluralName: entityType.name.plural,
      identifierKey: entityType.identifierKey,
      description: entityType.semantics?.entityType?.definition ?? '',
      type: entityType.type,
      semanticGroup: entityType.semanticGroup,
      tags: entityType.semantics?.entityType?.tags ?? [],

      icon: entityType.icon,
      columnConfiguration: entityType.columnConfiguration ?? {
        order: entityType.columns.map((col) => col.key),
        overrides: Object.fromEntries(
          entityType.columns.map((col) => [col.key, { width: col.width, visible: true }]),
        ),
      },
    },
  });

  // Create mutation function
  const { mutateAsync: updateType } = useSaveEntityTypeConfiguration(workspaceId, {
    onSuccess: () => {
      // These will be called from the store's handleSubmit
    },
  });

  // Create store only once per entity type
  if (!storeRef.current) {
    storeRef.current = createEntityTypeConfigStore(
      entityType.key,
      workspaceId,
      entityType,
      form,
      updateType,
    );
  }

  const { dirtyFields } = useFormState({
    control: form.control,
  });

  // Subscribe to form changes for dirty state tracking
  useEffect(() => {
    const store = storeRef.current?.getState();
    if (!store) return;

    const subscription = form.watch(() => {
      const dirty = Object.keys(dirtyFields).length > 0;
      store.setDirty(dirty);
    });

    return () => {
      subscription.unsubscribe();
    };
  }, [form, dirtyFields]);

  return (
    <EntityTypeConfigContext.Provider value={storeRef.current}>
      {children}
    </EntityTypeConfigContext.Provider>
  );
};

// Hook to access store with selector
const useEntityTypeConfigurationStore = <T,>(selector: (store: EntityTypeConfigStore) => T): T => {
  const context = useContext(EntityTypeConfigContext);

  if (!context) {
    throw new Error(
      'useEntityTypeConfigurationStore must be used within EntityTypeConfigurationProvider',
    );
  }

  return useStore(context, selector);
};

export const useConfigFormState = () => {
  return useEntityTypeConfigurationStore((state) => state);
};

export const useConfigCurrentType = () => {
  return useEntityTypeConfigurationStore((state) => state.entityType);
};

// Optimized hooks for common access patterns
export const useConfigForm = () => {
  return useEntityTypeConfigurationStore((state) => state.form);
};

export const useConfigIsDirty = () => {
  return useEntityTypeConfigurationStore((state) => state.isDirty);
};
