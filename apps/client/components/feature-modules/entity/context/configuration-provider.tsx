'use client';

import { EntityType, EntityPropertyType } from '@/lib/types/entity';
import { zodResolver } from '@hookform/resolvers/zod';
import { createContext, useContext, useEffect, useRef, type ReactNode } from 'react';
import { useForm, useFormState } from 'react-hook-form';
import { toast } from 'sonner';
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
    columns: z.array(
      z.object({
        key: z.string().min(1, 'Ordering key is required').refine(isUUID),
        type: z.nativeEnum(EntityPropertyType),
        width: z.number().min(150, 'Minimum width is 150').max(1000, 'Maximum width is 1000'),
      }),
    ),
  })
  .extend(baseEntityTypeFormSchema.shape);

export type EntityTypeFormValues = z.infer<typeof entityTypeFormSchema>;

export const EntityTypeConfigurationProvider = ({
  children,
  workspaceId,
  entityType,
}: EntityTypeConfigurationProviderProps) => {
  const storeRef = useRef<EntityTypeConfigStoreApi | null>(null);
  const unsavedToastRef = useRef<string | number | undefined>(undefined);

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
      columns: entityType.columns,
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

  // Load draft and set up form watchers on mount
  useEffect(() => {
    const store = storeRef.current?.getState();
    if (!store) return;

    // Check for draft and prompt user to restore
    const draft = store.loadDraft();
    if (draft && !unsavedToastRef.current) {
      unsavedToastRef.current = toast.info('Unsaved changes found', {
        description: 'Would you like to restore your previous changes?',
        action: {
          label: 'Restore',
          onClick: () => {
            form.reset(draft, {
              keepDefaultValues: true,
            });
            store.setDirty(true);
          },
        },
        cancel: {
          label: 'Dismiss',
          onClick: () => {
            store.clearDraft();
          },
        },
        onDismiss: () => {
          store.clearDraft();
        },
      });
    }
  }, [entityType.key]);

  const { dirtyFields } = useFormState({
    control: form.control,
  });

  // Subscribe to form changes for dirty state tracking and auto-save
  useEffect(() => {
    const store = storeRef.current?.getState();
    if (!store) return;

    const debouncedSaveRef = { current: null as NodeJS.Timeout | null };

    const subscription = form.watch((values) => {
      const dirty = Object.keys(dirtyFields).length > 0;
      store.setDirty(dirty);

      if (dirty) {
        if (debouncedSaveRef.current) {
          clearTimeout(debouncedSaveRef.current);
        }

        debouncedSaveRef.current = setTimeout(() => {
          const curr = form.getValues();
          store.saveDraft(curr);
        }, 1000);
      }
    });

    return () => {
      subscription.unsubscribe();
      if (debouncedSaveRef.current) {
        clearTimeout(debouncedSaveRef.current);
      }
    };
  }, [form]);

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
