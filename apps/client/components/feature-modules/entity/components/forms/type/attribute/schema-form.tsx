import { useEntityTypeAttributeSchemaForm } from '@/components/feature-modules/entity/hooks/form/type/use-schema-form';
import {
  Form,
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { IconSelector } from '@/components/ui/icon/icon-selector';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@riven/ui/select';
import { Switch } from '@/components/ui/switch';
import { Textarea } from '@/components/ui/textarea';
import { DialogControl } from '@/lib/interfaces/interface';
import { SchemaType } from '@/lib/types/common';
import {
  EntityAttributeDefinition,
  EntityType,
  EntityTypeSemanticMetadata,
  SemanticAttributeClassification,
} from '@/lib/types/entity';
import { cn } from '@/lib/util/utils';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';

import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { AnimatePresence, motion } from 'framer-motion';
import { Settings2 } from 'lucide-react';
import { FC, useEffect, useMemo } from 'react';
import { DefaultValueInput, TYPES_WITHOUT_DEFAULT } from './default-value-input';
import { EnumOptionsEditor } from '../../enum-options-editor';

const classificationOptions = [
  {
    value: SemanticAttributeClassification.Identifier,
    label: 'Identifier',
    description: 'A unique label that tells records apart',
    example: 'e.g. Order Number, Email Address, SKU',
  },
  {
    value: SemanticAttributeClassification.Categorical,
    label: 'Categorical',
    description: 'Puts records into groups you can filter by',
    example: 'e.g. Status, Department, Country',
  },
  {
    value: SemanticAttributeClassification.Quantitative,
    label: 'Quantitative',
    description: 'A number you might count, sum, or compare',
    example: 'e.g. Price, Quantity, Rating',
  },
  {
    value: SemanticAttributeClassification.Temporal,
    label: 'Temporal',
    description: 'A date or time something happened',
    example: 'e.g. Created Date, Due Date, Birthday',
  },
  {
    value: SemanticAttributeClassification.Freetext,
    label: 'Free Text',
    description: 'Open-ended text written by a person',
    example: 'e.g. Notes, Description, Bio',
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
  onSuccess?: (definitionId: string) => void;
}

export const SchemaForm: FC<Props> = ({
  currentType,
  attribute,
  type,
  dialog,
  workspaceId,
  semanticMetadata,
  onSuccess: onSuccessCallback,
}) => {
  const { open, setOpen } = dialog;

  const onSave = () => {
    setOpen(false);
  };

  const onCancel = () => {
    setOpen(false);
  };

  const { form, handleSubmit, handleReset, mode } = useEntityTypeAttributeSchemaForm(
    workspaceId,
    type,
    open,
    onSave,
    onCancel,
    attribute,
    semanticMetadata,
  );

  const isRequired = form.watch('required');
  const watchedEnumValues = form.watch('enumValues');

  const showDefaultValue =
    mode === 'edit' && isRequired && !TYPES_WITHOUT_DEFAULT.has(currentType);
  const disableRequiredInEdit = mode === 'edit' && TYPES_WITHOUT_DEFAULT.has(currentType);

  // Clear default value when required is toggled off
  useEffect(() => {
    if (!isRequired) {
      form.setValue('defaultValue', undefined);
    }
  }, [isRequired]);

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
      <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-0">
        {/* Section 1: Naming */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
            Naming
          </h3>

          <FormField
            control={form.control}
            name="name"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Attribute name</FormLabel>
                <FormDescription>How this attribute appears throughout the system</FormDescription>
                <div className="flex items-center gap-3">
                  <FormField
                    control={form.control}
                    name="icon"
                    render={({ field: iconField }) => (
                      <FormItem>
                        <IconSelector
                          onSelect={iconField.onChange}
                          icon={iconField.value}
                          className="size-9 bg-accent/10"
                          displayIconClassName="size-5"
                        />
                      </FormItem>
                    )}
                  />
                  <FormControl>
                    <Input placeholder="Enter attribute name" {...field} />
                  </FormControl>
                </div>
                <FormMessage />
              </FormItem>
            )}
          />
        </section>

        <div className="border-t" />

        {/* Section 2: Constraints */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
            Constraints
          </h3>
          <p className="text-sm text-muted-foreground">
            Define validation rules for this attribute
          </p>

          <FormField
            control={form.control}
            name="required"
            render={({ field }) => (
              <FormItem className="flex items-center justify-between space-y-0">
                <div className="space-y-0.5">
                  <FormLabel className="text-sm font-normal">Required</FormLabel>
                  <FormDescription className="text-xs">
                    {isIdentifierAttribute
                      ? 'This attribute is the identifier key and must be required'
                      : disableRequiredInEdit
                        ? 'This type does not support default values, so required can only be set when creating the attribute'
                        : 'Required attributes must have a value for each record'}
                  </FormDescription>
                </div>
                <FormControl>
                  <Switch
                    checked={field.value}
                    onCheckedChange={field.onChange}
                    disabled={isIdentifierAttribute || disableRequiredInEdit}
                  />
                </FormControl>
              </FormItem>
            )}
          />

          <AnimatePresence>
            {showDefaultValue && (
              <motion.div
                initial={{ opacity: 0, height: 0 }}
                animate={{ opacity: 1, height: 'auto' }}
                exit={{ opacity: 0, height: 0 }}
                transition={{ duration: 0.2 }}
                className="overflow-hidden"
              >
                <div className="mt-2 rounded-md border border-dashed bg-muted/30 p-4">
                  <DefaultValueInput
                    currentType={currentType}
                    form={form}
                    enumValues={watchedEnumValues?.filter(Boolean) as string[] | undefined}
                  />
                </div>
              </motion.div>
            )}
          </AnimatePresence>
          {allowUniqueness && (
            <FormField
              control={form.control}
              name="unique"
              render={({ field }) => (
                <FormItem className="flex items-center justify-between space-y-0">
                  <div className="space-y-0.5">
                    <FormLabel className="text-sm font-normal">Unique</FormLabel>
                    <FormDescription className="text-xs">
                      {isIdentifierAttribute
                        ? 'This attribute is the identifier key and must be unique'
                        : 'Unique attributes enforce distinct values across all records'}
                    </FormDescription>
                  </div>
                  <FormControl>
                    <Switch
                      checked={field.value}
                      onCheckedChange={field.onChange}
                      disabled={isIdentifierAttribute}
                    />
                  </FormControl>
                </FormItem>
              )}
            />
          )}

          {/* Schema Options */}
          {requireEnumOptions && <EnumOptionsEditor form={form} />}

          {/* Type-specific constraints popover */}
          {(requireNumericalValidation || requireStringValidation) && (
            <Popover>
              <PopoverTrigger asChild>
                <Button type="button" variant="outline" size="sm" className="text-muted-foreground">
                  <Settings2 className="mr-2 size-4" />
                  Value constraints
                </Button>
              </PopoverTrigger>
              <PopoverContent className="w-80 space-y-4" align="start">
                {requireNumericalValidation && (
                  <div className="grid grid-cols-2 gap-4">
                    <FormField
                      control={form.control}
                      name="minimum"
                      render={({ field }) => (
                        <FormItem>
                          <FormLabel>Minimum</FormLabel>
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
                          <FormLabel>Maximum</FormLabel>
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
                )}

                {requireStringValidation && (
                  <>
                    <div className="grid grid-cols-2 gap-4">
                      <FormField
                        control={form.control}
                        name="minLength"
                        render={({ field }) => (
                          <FormItem>
                            <FormLabel>Min length</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                placeholder="Min"
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
                            <FormLabel>Max length</FormLabel>
                            <FormControl>
                              <Input
                                type="number"
                                placeholder="Max"
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
                          <FormLabel>Regex pattern</FormLabel>
                          <FormControl>
                            <Input placeholder="^[A-Za-z]+$" {...field} value={field.value ?? ''} />
                          </FormControl>
                          <FormDescription className="text-xs">
                            Regular expression for validation
                          </FormDescription>
                          <FormMessage />
                        </FormItem>
                      )}
                    />
                  </>
                )}
              </PopoverContent>
            </Popover>
          )}
        </section>

        <div className="border-t" />

        {/* Section 3: Semantic Context */}
        <section className="space-y-4 px-6 py-5">
          <h3 className="text-xs font-medium tracking-wider text-muted-foreground uppercase">
            Semantic Context
          </h3>
          <p className="text-sm text-muted-foreground">
            Help the system understand this attribute by providing semantic metadata
          </p>

          <FormField
            control={form.control}
            name="classification"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Classification</FormLabel>
                {attribute && field.value ? (
                  (() => {
                    const option = classificationOptions.find((o) => o.value === field.value);
                    return (
                      <div className="flex w-fit flex-col gap-0.5 rounded-md border bg-muted/50 px-3 py-2">
                        <span className="text-sm">{option?.label ?? field.value}</span>
                        {option?.description && (
                          <span className="text-xs text-muted-foreground">
                            {option.description}
                          </span>
                        )}
                      </div>
                    );
                  })()
                ) : (
                  <>
                    <Select onValueChange={field.onChange} value={field.value ?? ''}>
                      <FormControl>
                        <SelectTrigger>
                          <SelectValue placeholder="Select classification...">
                            {field.value
                              ? classificationOptions.find((o) => o.value === field.value)?.label
                              : 'Select classification...'}
                          </SelectValue>
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        {classificationOptions.map((opt) => (
                          <SelectItem key={opt.value} value={opt.value} textValue={opt.label}>
                            <div className="flex flex-col gap-0.5">
                              <span>{opt.label}</span>
                              <span className="text-xs text-muted-foreground">
                                {opt.description}
                              </span>
                              <span className="text-xs text-muted-foreground/70 italic">
                                {opt.example}
                              </span>
                            </div>
                          </SelectItem>
                        ))}
                      </SelectContent>
                    </Select>
                    {classificationSuggestions[currentType] && !field.value && (
                      <p className="text-xs text-muted-foreground">
                        Suggested:{' '}
                        {
                          classificationOptions.find(
                            (o) => o.value === classificationSuggestions[currentType],
                          )?.label
                        }
                      </p>
                    )}
                  </>
                )}
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="definition"
            render={({ field }) => (
              <FormItem>
                <FormLabel>Definition</FormLabel>
                <FormDescription>
                  Help the system understand this attribute by describing it in plain language
                </FormDescription>
                <FormControl>
                  <Textarea
                    placeholder="e.g., The primary email address used for customer communications and account recovery"
                    className="min-h-16 resize-none"
                    rows={2}
                    style={{ fieldSizing: 'content' } as React.CSSProperties}
                    {...field}
                    value={field.value ?? ''}
                  />
                </FormControl>
                {field.value && (
                  <p
                    className={cn(
                      'text-right text-xs',
                      (field.value?.length ?? 0) > 500 ? 'text-amber-500' : 'text-muted-foreground',
                    )}
                  >
                    {field.value.length}/500
                  </p>
                )}
                <FormMessage />
              </FormItem>
            )}
          />
        </section>

        {/* Footer */}
        <footer className="flex justify-end gap-3 border-t px-6 py-4">
          <Button type="button" onClick={handleReset} variant="outline">
            Cancel
          </Button>
          <Button type="submit">{attribute ? 'Update Attribute' : 'Add Attribute'}</Button>
        </footer>
      </form>
    </Form>
  );
};
