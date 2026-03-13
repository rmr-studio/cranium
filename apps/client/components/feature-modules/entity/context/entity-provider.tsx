'use client';

import { Entity, EntityType } from '@/lib/types/entity';
import {
  buildDefaultValuesFromEntityType,
  buildZodSchemaFromEntityType,
} from '@/lib/util/form/entity-instance-validation.util';
import { zodResolver } from '@hookform/resolvers/zod';
import { createContext, useContext, useMemo, useRef, type ReactNode } from 'react';
import { FormProvider, useForm } from 'react-hook-form';
import { useStore } from 'zustand';
import { useSaveEntityMutation } from '../hooks/mutation/instance/use-save-entity-mutation';
import {
  createEntityDraftStore,
  EntityDraftStore,
  EntityDraftStoreApi,
} from '../stores/entity.store';

const EntityDraftContext = createContext<EntityDraftStoreApi | undefined>(undefined);

export interface EntityDraftProviderProps {
  children: ReactNode;
  workspaceId: string;
  entityType: EntityType;
  onEntityCreated?: (entity: Entity) => void;
}

export const EntityDraftProvider = ({
  children,
  workspaceId,
  entityType,
  onEntityCreated,
}: EntityDraftProviderProps) => {
  const storeRef = useRef<EntityDraftStoreApi | null>(null);

  // Build dynamic Zod schema from entity type
  const schema = useMemo(
    () => buildZodSchemaFromEntityType(entityType),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: version increments when schema changes
    [entityType.id, entityType.version],
  );

  // Build default values from entity type (includes attribute defaults and relationship arrays)
  const defaultValues = useMemo(
    () => buildDefaultValuesFromEntityType(entityType),
    // eslint-disable-next-line react-hooks/exhaustive-deps -- intentional: version increments when schema changes
    [entityType.id, entityType.version],
  );

  // Create form instance with dynamic schema
  const form = useForm<Record<string, any>>({
    resolver: zodResolver(schema),
    defaultValues,
    mode: 'onBlur', // Validate on blur (user preference)
  });

  // Create mutation for entity creation
  const { mutateAsync: saveEntity } = useSaveEntityMutation(workspaceId, entityType.id, {
    onSuccess: (response) => {
      // The mutation should already guard against a null entity, but :shrug:
      if (!response.entity) return;
      onEntityCreated?.(response.entity);
    },
  });

  // Create store only once per entity type
  if (!storeRef.current) {
    storeRef.current = createEntityDraftStore(workspaceId, entityType, form, saveEntity);
  }

  return (
    <EntityDraftContext.Provider value={storeRef.current}>
      <FormProvider {...form}>{children}</FormProvider>
    </EntityDraftContext.Provider>
  );
};

const useEntityDraftStore = <T,>(selector: (store: EntityDraftStore) => T): T => {
  const context = useContext(EntityDraftContext);

  if (!context) {
    throw new Error('useEntityDraftStore must be used within EntityDraftProvider');
  }

  return useStore(context, selector);
};

export const useEntityDraft = () => {
  return useEntityDraftStore((state) => state);
};
