import {
  FormControl,
  FormDescription,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@riven/ui/input';
import { Textarea } from '@riven/ui/textarea';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@riven/ui/select';
import { Separator } from '@/components/ui/separator';
import { TagInput } from '@/components/ui/tag-input';
import { SchemaType } from '@/lib/types/common';
import { EntityAttributeDefinition, SemanticGroup } from '@/lib/types/entity';
import { cn } from '@/lib/util/utils';
import { Info } from 'lucide-react';
import { FC, useEffect, useMemo } from 'react';
import { useWatch } from 'react-hook-form';
import { useConfigCurrentType, useConfigForm } from '@/components/feature-modules/entity/context/configuration-provider';

const CATEGORY_LABELS: Record<SemanticGroup, string> = {
  [SemanticGroup.Customer]: 'Customer',
  [SemanticGroup.Product]: 'Product',
  [SemanticGroup.Transaction]: 'Transaction',
  [SemanticGroup.Communication]: 'Communication',
  [SemanticGroup.Support]: 'Support',
  [SemanticGroup.Financial]: 'Financial',
  [SemanticGroup.Operational]: 'Operational',
  [SemanticGroup.Custom]: 'Custom',
  [SemanticGroup.Uncategorized]: 'Uncategorized',
};

interface Props {
  availableIdentifiers: EntityAttributeDefinition[];
}

export const ConfigurationForm: FC<Props> = ({ availableIdentifiers }) => {
  const form = useConfigForm();
  const entityType = useConfigCurrentType();

  const idAttribute = useMemo(() => {
    if (!entityType?.schema?.properties) return undefined;
    return Object.entries(entityType.schema.properties).find(
      ([, attr]) => attr.key === SchemaType.Id,
    );
  }, [entityType?.schema?.properties]);

  const identifierKey = useWatch({
    control: form.control,
    name: 'identifierKey',
  });

  const columnConfiguration = useWatch({
    control: form.control,
    name: 'columnConfiguration',
  });

  const semanticGroup = useWatch({
    control: form.control,
    name: 'semanticGroup',
  });

  const description = useWatch({
    control: form.control,
    name: 'description',
  });

  // Watch for identifier key changes and auto-reorder to ensure identifier is first
  useEffect(() => {
    const order = columnConfiguration.order;
    if (order.length <= 1) return;

    const idx = order.indexOf(identifierKey);
    if (idx === 0) return; // Already first

    if (idx > 0) {
      // Move identifier to front
      const newOrder = [identifierKey, ...order.filter((_, i) => i !== idx)];
      form.setValue('columnConfiguration.order', newOrder, { shouldDirty: true });
    } else {
      // Identifier not in order array, add it at front
      form.setValue('columnConfiguration.order', [identifierKey, ...order], { shouldDirty: true });
    }
  }, [identifierKey, form, columnConfiguration.order]);

  return (
    <div className="space-y-0 rounded-lg border bg-card">
      {/* Naming */}
      <div className="p-5">
        <p className="mb-3.5 text-xs font-medium tracking-widest text-muted-foreground/70 uppercase">
          Naming
        </p>
        <div className="grid grid-cols-2 gap-5">
          <FormField
            control={form.control}
            name="pluralName"
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel className="text-sm font-medium text-muted-foreground">
                  Plural noun
                </FormLabel>
                <FormDescription className="text-xs italic">
                  Used to label a collection of these entities
                </FormDescription>
                <FormControl>
                  <Input placeholder="e.g., Companies" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="singularName"
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel className="text-sm font-medium text-muted-foreground">
                  Singular noun
                </FormLabel>
                <FormDescription className="text-xs italic">
                  How a single entity of this type is labelled
                </FormDescription>
                <FormControl>
                  <Input placeholder="e.g., Company" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      </div>

      <Separator />

      {/* Description */}
      <div className="p-5">
        <FormField
          control={form.control}
          name="description"
          render={({ field }) => (
            <FormItem className="gap-1">
              <FormLabel className="text-sm font-medium text-muted-foreground">
                Description
              </FormLabel>
              <FormDescription className="text-xs italic">
                A brief summary of what this entity type represents
              </FormDescription>
              <FormControl>
                <Textarea
                  className={cn(
                    'max-h-72 resize-none',
                    semanticGroup === SemanticGroup.Custom && !description && 'border-amber-500/40',
                  )}
                  placeholder="Describe what this entity type represents..."
                  rows={2}
                  {...field}
                />
              </FormControl>
              {semanticGroup === SemanticGroup.Custom && !description && (
                <p className="flex items-center gap-1.5 pt-0.5 text-xs text-amber-600 dark:text-amber-400">
                  <Info className="size-3 shrink-0" />
                  A description helps the system understand this type&apos;s purpose
                </p>
              )}
              <FormMessage />
            </FormItem>
          )}
        />
      </div>

      <Separator />

      {/* Classification */}
      <div className="p-5">
        <p className="mb-3.5 text-xs font-medium tracking-widest text-muted-foreground/70 uppercase">
          Classification
        </p>
        <div className="grid grid-cols-2 gap-5">
          <FormField
            control={form.control}
            name="semanticGroup"
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel className="text-sm font-medium text-muted-foreground">
                  Category
                </FormLabel>
                <FormDescription className="text-xs italic">
                  The domain this type belongs to
                </FormDescription>
                <Select onValueChange={field.onChange} value={field.value}>
                  <FormControl>
                    <SelectTrigger className="w-full">
                      <SelectValue>
                        {CATEGORY_LABELS[field.value as SemanticGroup]}
                      </SelectValue>
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {Object.values(SemanticGroup).map((value) => (
                      <SelectItem key={value} value={value}>
                        {CATEGORY_LABELS[value]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
                <FormMessage />
              </FormItem>
            )}
          />
          <FormField
            control={form.control}
            name="tags"
            render={({ field }) => (
              <FormItem className="gap-1">
                <FormLabel className="text-sm font-medium text-muted-foreground">
                  Tags
                </FormLabel>
                <FormDescription className="text-xs italic">
                  Labels to help organise and filter your types
                </FormDescription>
                <FormControl>
                  <TagInput
                    value={field.value}
                    onChange={field.onChange}
                    placeholder="Add tags..."
                  />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />
        </div>
      </div>

      {idAttribute && (
        <>
          <Separator />
          <div className="p-5">
            <p className="mb-3.5 text-xs font-medium tracking-widest text-muted-foreground/70 uppercase">
              Record ID
            </p>
            <FormField
              control={form.control}
              name="idPrefix"
              render={({ field }) => (
                <FormItem className="gap-1">
                  <FormLabel className="text-sm font-medium text-muted-foreground">
                    ID Prefix
                  </FormLabel>
                  <FormDescription className="text-xs italic">
                    Records will be numbered as PREFIX-1, PREFIX-2, etc.
                  </FormDescription>
                  <FormControl>
                    <Input
                      placeholder="e.g., TKT"
                      maxLength={10}
                      className="w-full max-w-xs uppercase"
                      {...field}
                      onChange={(e) => field.onChange(e.target.value.toUpperCase())}
                      value={field.value}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
          </div>
        </>
      )}

      <Separator />

      {/* Identity */}
      <div className="p-5">
        <p className="mb-3.5 text-xs font-medium tracking-widest text-muted-foreground/70 uppercase">
          Identity
        </p>
        <FormField
          control={form.control}
          name="identifierKey"
          render={({ field }) => (
            <FormItem className="gap-1">
              <FormLabel className="text-sm font-medium text-muted-foreground">
                Identifier attribute
              </FormLabel>
              <Select onValueChange={field.onChange} value={field.value}>
                <FormControl>
                  <SelectTrigger className="w-full max-w-xs">
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
              <p className="max-w-sm pt-0.5 text-xs leading-relaxed text-muted-foreground/70">
                Must point to an attribute marked as unique and mandatory.
              </p>
              <FormMessage />
            </FormItem>
          )}
        />
      </div>
    </div>
  );
};
