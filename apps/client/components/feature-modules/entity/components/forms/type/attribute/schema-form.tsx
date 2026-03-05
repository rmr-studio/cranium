import { useEntityTypeAttributeSchemaForm } from '@/components/feature-modules/entity/hooks/form/type/use-schema-form';
import { Button, Input } from '@riven/ui';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Switch } from '@/components/ui/switch';
import { DialogControl } from '@/lib/interfaces/interface';
import { EntityAttributeDefinition, EntityType } from '@/lib/types/entity';
import { SchemaType } from '@/lib/types/common';
import { FC, useEffect, useMemo } from 'react';
import { EnumOptionsEditor } from '../../enum-options-editor';

interface Props {
  workspaceId: string;
  dialog: DialogControl;
  currentType: SchemaType;
  attribute?: EntityAttributeDefinition;
  type: EntityType;
}

export const SchemaForm: FC<Props> = ({ currentType, attribute, type, dialog, workspaceId }) => {
  const { open, setOpen } = dialog;

  const onSave = () => {
    setOpen(false);
  };

  const onCancel = () => {
    setOpen(false);
  };

  const { form, handleSubmit, handleReset } = useEntityTypeAttributeSchemaForm(
    workspaceId,
    type,
    open,
    onSave,
    onCancel,
    attribute,
  );

  // Determine what schema options to show based on the selected type
  const requireEnumOptions = [SchemaType.Select, SchemaType.MultiSelect].includes(currentType);
  const requireNumericalValidation = currentType == SchemaType.Number;
  const requireStringValidation = [SchemaType.Text, SchemaType.Email, SchemaType.Phone].includes(
    currentType,
  );

  const allowUniqueness = [
    SchemaType.Text,
    SchemaType.Email,
    SchemaType.Phone,
    SchemaType.Number,
  ].includes(currentType); // Add types that allow uniqueness here

  // Adjust Schema type inside form based on AttributeTypeDropdown value in outer component
  useEffect(() => {
    form.setValue('selectedType', currentType);
  }, [currentType]);

  const isIdentifierAttribute: boolean = useMemo(() => {
    if (!attribute) return false;
    return attribute.id === type.identifierKey;
  }, [attribute, type]);

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmit)} className="flex flex-col space-y-4">
        <FormField
          control={form.control}
          name="name"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Name</FormLabel>
              <FormControl>
                <Input placeholder="Enter attribute name" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <div className="space-y-4">
          <FormField
            control={form.control}
            name="required"
            render={({ field }) => (
              <>
                <FormItem className="mb-1 flex items-center justify-between space-y-0">
                  <FormLabel>Required</FormLabel>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      disabled={isIdentifierAttribute}
                    />
                  </FormControl>
                </FormItem>
                <FormDescription className="text-xs italic">
                  {isIdentifierAttribute
                    ? 'This attribute is the identifier key and must be required'
                    : 'Required attributes must have a value for each record'}
                </FormDescription>
              </>
            )}
          />
          {allowUniqueness && (
            <FormField
              control={form.control}
              name="unique"
              render={({ field }) => (
                <>
                  <FormItem className="mb-1 flex items-center justify-between space-y-0">
                    <FormLabel>Unique</FormLabel>
                    <FormControl>
                      <Switch
                        checked={field.value}
                        onCheckedChange={field.onChange}
                        disabled={isIdentifierAttribute}
                      />
                    </FormControl>
                  </FormItem>
                  <FormDescription className="text-xs italic">
                    {isIdentifierAttribute
                      ? 'This attribute is the identifier key and must be unique'
                      : 'Unique attributes enforce distinct values across all records. There can be only one record with a given value.'}
                  </FormDescription>
                </>
              )}
            />
          )}
        </div>

        {/* Schema Options */}
        {requireEnumOptions && <EnumOptionsEditor form={form} />}

        {requireNumericalValidation && (
          <div className="border-t pt-4">
            <h3 className="mb-3 text-sm font-medium">Value Constraints</h3>
            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="minimum"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Minimum Value</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        placeholder="Min"
                        {...field}
                        value={field.value ?? ''}
                        onChange={(e) =>
                          field.onChange(
                            e.target.value === '' ? undefined : parseFloat(e.target.value),
                          )
                        }
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="maximum"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Maximum Value</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        placeholder="Max"
                        {...field}
                        value={field.value ?? ''}
                        onChange={(e) =>
                          field.onChange(
                            e.target.value === '' ? undefined : parseFloat(e.target.value),
                          )
                        }
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </div>
        )}

        {requireStringValidation && (
          <div className="border-t pt-4">
            <h3 className="mb-3 text-sm font-medium">String Constraints</h3>
            <div className="space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <FormField
                  control={form.control}
                  name="minLength"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Minimum Length</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Min length"
                          {...field}
                          value={field.value ?? ''}
                          onChange={(e) =>
                            field.onChange(
                              e.target.value === '' ? undefined : parseInt(e.target.value),
                            )
                          }
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="maxLength"
                  render={({ field }) => (
                    <FormItem>
                      <FormLabel>Maximum Length</FormLabel>
                      <FormControl>
                        <Input
                          type="number"
                          placeholder="Max length"
                          {...field}
                          value={field.value ?? ''}
                          onChange={(e) =>
                            field.onChange(
                              e.target.value === '' ? undefined : parseInt(e.target.value),
                            )
                          }
                        />
                      </FormControl>
                      <FormMessage />
                    </FormItem>
                  )}
                />
              </div>
              <FormField
                control={form.control}
                name="regex"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Regex Pattern (Optional)</FormLabel>
                    <FormControl>
                      <Input placeholder="^[A-Za-z]+$" {...field} value={field.value ?? ''} />
                    </FormControl>
                    <FormDescription className="text-xs">
                      Regular expression pattern for validation
                    </FormDescription>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
          </div>
        )}
        <footer className="mt-4 flex justify-end space-x-4 border-t pt-4">
          <Button type="button" onClick={handleReset} variant={'destructive'}>
            Cancel
          </Button>
          <Button type="submit">{attribute ? 'Update Schema' : 'Add Attribute'}</Button>
        </footer>
      </form>
    </Form>
  );
};
