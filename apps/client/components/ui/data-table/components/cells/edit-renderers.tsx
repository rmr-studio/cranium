'use client';

import { FormWidgetProps } from '@/components/feature-modules/blocks/components/forms';
import { getWidgetForSchema } from '@/components/feature-modules/entity/components/forms/instance/entity-field-registry';
import { EntityRelationshipPicker } from '@/components/feature-modules/entity/components/forms/instance/entity-relationship-picker';
import { FormField } from '@/components/ui/form';
import { SchemaUUID } from '@/lib/types/common';
import { EntityLink, EntityRelationshipDefinition } from '@/lib/types/entity';
import { FC } from 'react';
import { useFormState, useWatch } from 'react-hook-form';
import { EditRenderProps } from '../../data-table.types';

/**
 * Creates an attribute edit renderer using schema-based widgets
 *
 * @example
 * ```tsx
 * const columns = [
 *   {
 *     accessorKey: "name",
 *     meta: {
 *       edit: {
 *         enabled: true,
 *         render: createAttributeRenderer(schema),
 *       }
 *     }
 *   }
 * ]
 * ```
 */
export function createAttributeRenderer<TData>(
  schema: SchemaUUID,
): (props: EditRenderProps<TData, unknown>) => React.ReactNode {
  return function AttributeEditRenderer({ form, onSave }) {
    const Widget: FC<FormWidgetProps> = getWidgetForSchema(schema);

    // Extract error messages
    const { errors } = useFormState({ control: form.control, name: 'value' });
    const fieldError = errors['value'];
    const errorMessages = fieldError?.message
      ? [String(fieldError.message)]
      : fieldError?.type
        ? [String(fieldError.type)]
        : undefined;

    // Trigger validation on blur
    const handleBlur = async () => {
      await form.trigger('value');
      await onSave();
    };

    if (!Widget) {
      return <div className="text-sm text-muted-foreground">Unsupported type: {schema.key}</div>;
    }

    return (
      <FormField
        control={form.control}
        name="value"
        render={({ field }) => (
          <Widget
            value={field.value}
            onChange={field.onChange}
            onBlur={handleBlur}
            schema={schema}
            displayError="tooltip"
            errors={errorMessages}
            autoFocus
            options={
              schema.options?._enum
                ? schema.options._enum.map((opt) => ({
                    label: opt,
                    value: opt,
                  }))
                : undefined
            }
          />
        )}
      />
    );
  };
}

/**
 * Creates a relationship edit renderer using EntityRelationshipPicker
 *
 * @example
 * ```tsx
 * const columns = [
 *   {
 *     accessorKey: "assignedTo",
 *     meta: {
 *       edit: {
 *         enabled: true,
 *         render: createRelationshipRenderer(relationship),
 *       }
 *     }
 *   }
 * ]
 * ```
 */
export function createRelationshipRenderer<TData>(
  relationship: EntityRelationshipDefinition,
  isTargetSide?: boolean,
): (props: EditRenderProps<TData, EntityLink[]>) => React.ReactNode {
  return function RelationshipEditRenderer({ form, onSave }) {
    const value: EntityLink[] = useWatch({
      control: form.control,
      name: 'value',
    });

    // Extract error messages
    const { errors } = useFormState({ control: form.control, name: 'value' });

    const fieldError = errors['value'];
    const errorMessages = fieldError?.message
      ? [String(fieldError.message)]
      : fieldError?.type
        ? [String(fieldError.type)]
        : undefined;

    const handleChange = (newValue: EntityLink[]) => {
      form.setValue('value', newValue);
    };

    const handleRemove = (entityId: string) => {
      const current = form.getValues('value') ?? [];
      form.setValue(
        'value',
        current.filter((link) => link.id !== entityId),
      );
    };

    const onBlur = async () => {
      await form.trigger('value');
      await onSave();
    };

    return (
      <EntityRelationshipPicker
        relationship={relationship}
        isTargetSide={isTargetSide}
        autoFocus
        value={value}
        errors={errorMessages}
        handleBlur={onBlur}
        handleChange={handleChange}
        handleRemove={handleRemove}
      />
    );
  };
}
