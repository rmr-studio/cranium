'use client';

import { entityFieldWidgetRegistry } from '@/components/feature-modules/entity/components/forms/instance/entity-field-registry';
import { AttributeFormValues } from '@/components/feature-modules/entity/hooks/form/type/use-schema-form';
import { SchemaType } from '@/lib/types/common';
import { SchemaUUID } from '@/lib/types/common';
import { attributeTypes } from '@/lib/util/form/schema.util';
import { FC } from 'react';
import { UseFormReturn } from 'react-hook-form';
import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
} from '@/components/ui/form';

export const TYPES_WITHOUT_DEFAULT = new Set([
  SchemaType.FileAttachment,
  SchemaType.Object,
  SchemaType.Location,
]);

interface DefaultValueInputProps {
  currentType: SchemaType;
  form: UseFormReturn<AttributeFormValues>;
  enumValues?: string[];
}

export const DefaultValueInput: FC<DefaultValueInputProps> = ({
  currentType,
  form,
  enumValues,
}) => {
  if (TYPES_WITHOUT_DEFAULT.has(currentType)) return null;

  const Widget = entityFieldWidgetRegistry[currentType];
  if (!Widget) return null;

  const attrType = attributeTypes[currentType];
  if (!attrType) return null;

  const schema: SchemaUUID = {
    key: attrType.key,
    type: attrType.type,
    format: attrType.format,
    icon: attrType.icon,
    required: false,
    unique: false,
    _protected: false,
    options: attrType.options,
  };

  const options =
    enumValues && enumValues.length > 0
      ? enumValues.map((v) => ({ label: v, value: v }))
      : undefined;

  return (
    <FormField
      control={form.control}
      name="defaultValue"
      render={({ field }) => (
        <FormItem>
          <FormLabel className="text-sm font-normal">Default value</FormLabel>
          <FormDescription className="text-xs">
            Existing records with no value will be backfilled with this default
          </FormDescription>
          <FormControl>
            <Widget
              value={field.value ?? (currentType === SchemaType.Checkbox ? false : '')}
              onChange={field.onChange}
              onBlur={field.onBlur}
              schema={schema}
              placeholder="Enter default value..."
              options={options}
            />
          </FormControl>
        </FormItem>
      )}
    />
  );
};
