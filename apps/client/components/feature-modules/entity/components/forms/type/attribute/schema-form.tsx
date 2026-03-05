import { useEntityTypeAttributeSchemaForm } from '@/components/feature-modules/entity/hooks/form/type/use-schema-form';
import { Button } from '@/components/ui/button';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@/components/ui/input';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { DialogControl } from '@/lib/interfaces/interface';
import {
  EntityAttributeDefinition,
  EntityType,
  EntityTypeSemanticMetadata,
  SemanticAttributeClassification,
} from '@/lib/types/entity';
import { SchemaType } from '@/lib/types/common';
import { cn } from '@/lib/util/utils';
import { FC, useEffect, useMemo } from 'react';
import { EnumOptionsEditor } from '../../enum-options-editor';

const classificationOptions = [
  {
    value: SemanticAttributeClassification.Identifier,
    label: 'Identifier',
    description: 'Uniquely identifies an entity instance',
  },
  {
    value: SemanticAttributeClassification.Categorical,
    label: 'Categorical',
    description: 'Groups or classifies entities into categories',
  },
  {
    value: SemanticAttributeClassification.Quantitative,
    label: 'Quantitative',
    description: 'A measurable numeric value or amount',
  },
  {
    value: SemanticAttributeClassification.Temporal,
    label: 'Temporal',
    description: 'Represents a point or period in time',
  },
  {
    value: SemanticAttributeClassification.Freetext,
    label: 'Free Text',
    description: 'Unstructured descriptive or narrative content',
  },
  {
    value: SemanticAttributeClassification.RelationalReference,
    label: 'Relational Reference',
    description: 'References another entity or external system',
  },
];

const classificationSuggestions: Partial<Record<SchemaType, SemanticAttributeClassification>> = {
  [SchemaType.Number]: SemanticAttributeClassification.Quantitative,
  [SchemaType.Currency]: SemanticAttributeClassification.Quantitative,
  [SchemaType.Percentage]: SemanticAttributeClassification.Quantitative,
  [SchemaType.Rating]: SemanticAttributeClassification.Quantitative,
  [SchemaType.Date]: SemanticAttributeClassification.Temporal,
  [SchemaType.Datetime]: SemanticAttributeClassification.Temporal,
  [SchemaType.Text]: SemanticAttributeClassification.Freetext,
  [SchemaType.Email]: SemanticAttributeClassification.Identifier,
  [SchemaType.Phone]: SemanticAttributeClassification.Identifier,
  [SchemaType.Url]: SemanticAttributeClassification.Identifier,
  [SchemaType.Select]: SemanticAttributeClassification.Categorical,
  [SchemaType.MultiSelect]: SemanticAttributeClassification.Categorical,
  [SchemaType.Checkbox]: SemanticAttributeClassification.Categorical,
};

interface Props {
  workspaceId: string;
  dialog: DialogControl;
  currentType: SchemaType;
  attribute?: EntityAttributeDefinition;
  type: EntityType;
  semanticMetadata?: EntityTypeSemanticMetadata;
}

export const SchemaForm: FC<Props> = ({
  currentType,
  attribute,
  type,
  dialog,
  workspaceId,
  semanticMetadata,
}) => {
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
    semanticMetadata,
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
        {/* Semantic Context */}
        <div className="border-t pt-4">
          <h3 className="mb-3 text-sm font-medium">Semantic Context</h3>

          <FormField
            control={form.control}
            name="classification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Classification</FormLabel>
                <Select
                  onValueChange={field.onChange}
                  value={field.value ?? undefined}
                >
                  <FormControl>
                    <SelectTrigger>
                      <SelectValue placeholder="Select classification..." />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {classificationOptions.map((opt) => (
                      <SelectItem key={opt.value} value={opt.value}>
                        <div className="flex flex-col">
                          <span>{opt.label}</span>
                          <span className="text-xs text-muted-foreground">
                            {opt.description}
                          </span>
                        </div>
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                {!attribute && classificationSuggestions[currentType] && !field.value && (
                  <p className="text-xs text-muted-foreground">
                    Suggested:{' '}
                    {
                      classificationOptions.find(
                        (o) => o.value === classificationSuggestions[currentType],
                      )?.label
                    }
                  </p>
                )}
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="definition"
            render={({ field }) => (
              <FormItem className="mt-4">
                <FormLabel>What does this attribute represent?</FormLabel>
                <FormControl>
                  <Textarea
                    placeholder="e.g., The primary email address used for customer communications and account recovery"
                    className="min-h-16 resize-none"
                    rows={3}
                    style={{ fieldSizing: 'content' } as React.CSSProperties}
                    {...field}
                    value={field.value ?? ''}
                  />
                </FormControl>
                {field.value && (
                  <p
                    className={cn(
                      'text-xs text-right',
                      (field.value?.length ?? 0) > 500
                        ? 'text-amber-500'
                        : 'text-muted-foreground',
                    )}
                  >
                    {field.value.length}/500
                  </p>
                )}
                <FormMessage />
              </FormItem>
            )}
          />
        </div>

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
