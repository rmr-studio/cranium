import { EntityType, UpdateEntityTypeConfigurationRequest } from '@/lib/types/entity';
import { UseFormReturn } from 'react-hook-form';
import { create, StoreApi } from 'zustand';
import { subscribeWithSelector } from 'zustand/middleware';
import { type EntityTypeFormValues } from '../../context/configuration-provider';

// State interface
interface EntityTypeConfigState {
  // Entity type identifier
  entityTypeKey: string;

  // Workspace and entity type data
  workspaceId: string;
  entityType: EntityType;

  // React Hook Form instance (stored for cross-component access)
  // Note: No longer nullable - form is initialized in the provider before store creation
  form: UseFormReturn<EntityTypeFormValues>;

  // Derived state
  isDirty: boolean;

  // Draft values for persistence (synced from form)
  draftValues: Partial<EntityTypeFormValues> | null;

  // Timestamp for staleness detection
  lastSavedAt: number | null;
  lastModifiedAt: number | null;
}

// Actions interface
interface EntityTypeConfigActions {
  // Update dirty state (called from form subscription)
  setDirty: (isDirty: boolean) => void;

  // Save draft to localStorage
  saveDraft: (values: Partial<EntityTypeFormValues>) => void;

  // Load draft from localStorage
  loadDraft: () => Partial<EntityTypeFormValues> | null;

  // Clear draft after successful save
  clearDraft: () => void;

  // Mark as saved
  markSaved: () => void;

  // Submit handler for form
  handleSubmit: (values: EntityTypeFormValues) => Promise<void>;

  // Reset store
  reset: () => void;
}

export type EntityTypeConfigStore = EntityTypeConfigState & EntityTypeConfigActions;

// Store factory (per-entity-type instances)
export const createEntityTypeConfigStore = (
  entityTypeKey: string,
  workspaceId: string,
  entityType: EntityType,
  form: UseFormReturn<EntityTypeFormValues>,
  updateMutation: (request: UpdateEntityTypeConfigurationRequest) => Promise<EntityType>,
): StoreApi<EntityTypeConfigStore> => {
  const storageKey = `${workspaceId}-entity-type-draft-${entityTypeKey}`;

  return create<EntityTypeConfigStore>()(
    subscribeWithSelector((set, get) => ({
      // Initial state
      entityTypeKey,
      workspaceId,
      entityType,
      form,
      isDirty: false,
      draftValues: null,
      lastSavedAt: null,
      lastModifiedAt: null,

      setDirty: (isDirty) => {
        set({
          isDirty,
          lastModifiedAt: isDirty ? Date.now() : get().lastModifiedAt,
        });
      },

      saveDraft: (values) => {
        const draft = { ...values, _timestamp: Date.now() };
        localStorage.setItem(storageKey, JSON.stringify(draft));
        set({
          draftValues: values,
          lastModifiedAt: Date.now(),
        });
      },

      loadDraft: () => {
        try {
          const stored = localStorage.getItem(storageKey);
          if (!stored) return null;

          const draft = JSON.parse(stored);
          const { _timestamp, ...values } = draft;

          // Check if draft is stale (older than 7 days)
          const isStale = Date.now() - _timestamp > 7 * 24 * 60 * 60 * 1000;
          if (isStale) {
            localStorage.removeItem(storageKey);
            return null;
          }

          set({ draftValues: values });
          return values;
        } catch (error) {
          console.error('Failed to load draft:', error);
          return null;
        }
      },

      clearDraft: () => {
        localStorage.removeItem(storageKey);
        set({ draftValues: null });
      },

      markSaved: () => {
        const now = Date.now();
        set({
          isDirty: false,
          lastSavedAt: now,
          lastModifiedAt: now,
        });
        get().clearDraft();
      },

      handleSubmit: async (values: EntityTypeFormValues): Promise<void> => {
        const { form, entityType } = get();

        // Validation: Identifier key must reference a unique attribute
        if (!entityType.schema.properties) return;

        const identifierAttribute = Object.entries(entityType.schema.properties).find(
          ([key]) => key === values.identifierKey,
        );

        if (!identifierAttribute) {
          form.setError('identifierKey', {
            type: 'manual',
            message: 'The identifier key must reference an existing attribute',
          });
          return;
        }

        const [, attrSchema] = identifierAttribute;

        if (!attrSchema.unique || !attrSchema.required) {
          form.setError('identifierKey', {
            type: 'manual',
            message: 'The identifier key must reference a mandatory unique attribute',
          });
          return;
        }

        // Clear any previous errors
        form.clearErrors();

        const request: UpdateEntityTypeConfigurationRequest = {
          id: entityType.id,
          name: {
            singular: values.singularName as string,
            plural: values.pluralName as string,
          },
          icon: values.icon,
          semanticGroup: values.semanticGroup,
          columnConfiguration: values.columnConfiguration,
          semantics:
            values.description || values.tags.length > 0
              ? { definition: values.description, tags: values.tags }
              : undefined,
        };

        // Call the mutation
        await updateMutation(request);

        // Mark as saved in store and clear draft

        // Reset form dirty state
        form.reset(form.getValues());
        get().markSaved();
      },

      reset: () => {
        set({
          isDirty: false,
          draftValues: null,
          lastSavedAt: null,
          lastModifiedAt: null,
        });
      },
    })),
  );
};
