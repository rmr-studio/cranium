import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input, Textarea } from '@riven/ui';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { EntityAttributeDefinition, EntityPropertyType } from '@/lib/types/entity';
import { FC, useEffect } from 'react';
import { useWatch } from 'react-hook-form';
import { useConfigForm } from '../../../context/configuration-provider';

interface Props {
  availableIdentifiers: EntityAttributeDefinition[];
}

export const ConfigurationForm: FC<Props> = ({ availableIdentifiers }) => {
  const form = useConfigForm();

  const identifierKey = useWatch({
    control: form.control,
    name: 'identifierKey',
  });

  const columns = useWatch({
    control: form.control,
    name: 'columns',
  });

  // Watch for identifier key changes and auto-recolumns to ensure identifier is first
  useEffect(() => {
    if (columns.length <= 1) return;

    // Check if identifier is already first
    const firstItem = columns[0];
    if (firstItem?.key === identifierKey && firstItem?.type === EntityPropertyType.Attribute) {
      // Already in first position, nothing to do
      return;
    }

    // Find identifier in current columns
    const identifierIndex = columns.findIndex(
      (item) => item.key === identifierKey && item.type === EntityPropertyType.Attribute,
    );

    if (identifierIndex !== -1) {
      // Identifier exists in columns, move it to first position
      const identifierItem = columns[identifierIndex];
      form.setValue(
        'columns',
        [identifierItem, ...columns.filter((_, idx) => idx !== identifierIndex)],
        {
          shouldDirty: true,
        },
      );
      return;
    }
    // Identifier not in columns, add it at first position
    form.setValue(
      'columns',
      [
        {
          key: identifierKey,
          type: EntityPropertyType.Attribute,
          width: 150,
        },
        ...columns,
      ],
      { shouldDirty: true },
    );

    // Update the columns in the form
  }, [identifierKey]);

  return (
    <div className="rounded-lg border bg-card p-6">
      <h2 className="mb-4 text-lg font-semibold">General</h2>

      <div className="space-y-6">
        <div className="grid grid-cols-2 items-start gap-6">
          {/* Name */}
          <FormField
            control={form.control}
            name="pluralName"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="font-semibold">Plural noun</FormLabel>
                <FormDescription className="text-xs italic">
                  This will be used to label a collection of these entities
                </FormDescription>
                <div className="flex items-center gap-2">
                  <FormControl>
                    <Input placeholder="e.g., Companies" {...field} />
                  </FormControl>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />

          {/* Plural Name */}
          <FormField
            control={form.control}
            name="singularName"
            render={({ field }) => (
              <FormItem>
                <FormLabel className="font-semibold">Singular noun</FormLabel>
                <FormDescription className="text-xs italic">
                  How we should label a single entity of this type
                </FormDescription>
                <div className="flex items-center gap-2">
                  <FormControl>
                    <Input placeholder="e.g., Company" {...field} />
                  </FormControl>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

        {/* Description */}
        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Description</FormLabel>
              <FormControl>
                <Textarea
                  placeholder="Describe what this entity type represents..."
                  rows={3}
                  {...field}
                />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
        {/* Identifier Key */}
        <FormField
          control={form.control}
          name="identifierKey"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Identifier Key</FormLabel>
              <Select onValueChange={field.onChange} value={field.value}>
                <FormControl>
                  <SelectTrigger className="w-xs">
                    <SelectValue placeholder="Select a unique identifier" />
                  </SelectTrigger>
                </FormControl>
                <SelectContent>
                  {availableIdentifiers
                    .filter((attr) => !!attr.schema.label)
                    .map((attr) => (
                      <SelectItem key={attr.id} value={attr.id}>
                        {attr.schema.label}
                      </SelectItem>
                    ))}
                  {availableIdentifiers.length === 0 && (
                    <SelectItem value="name" disabled>
                      No unique attributes available
                    </SelectItem>
                  )}
                </SelectContent>
              </Select>
              <FormDescription className="mx-1 max-w-sm">
                This attribute will be used to uniquely identify an entity, and must point to an
                attribute marked as unique, and mandatory.
              </FormDescription>
              <FormMessage />
            </FormItem>
          )}
        />
      </div>
    </div>
  );
};
