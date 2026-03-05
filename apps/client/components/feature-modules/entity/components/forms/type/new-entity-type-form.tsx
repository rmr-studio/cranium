'use client';

import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { Popover, PopoverContent, PopoverTrigger } from '@riven/ui/popover';
import { Textarea } from '@riven/ui/textarea';
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
} from '@/components/ui/select';
import { TagInput } from '@/components/ui/tag-input';
import type { ChildNodeProps } from '@riven/utils';
import { EntityType, SemanticGroup } from '@/lib/types/entity';
import { cn } from '@/lib/util/utils';
import { PopoverClose } from '@radix-ui/react-popover';
import { Plus } from 'lucide-react';
import { FC } from 'react';
import {
  NewEntityTypeFormValues,
  useNewEntityTypeForm,
} from '../../../hooks/form/type/use-new-type-form';

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

interface Props extends ChildNodeProps {
  entityTypes?: EntityType[];
  workspaceId: string;
}

export const NewEntityTypeForm: FC<Props> = ({ workspaceId, children }) => {
  const { form, handleSubmit } = useNewEntityTypeForm(workspaceId);

  const semanticGroup = form.watch('semanticGroup');

  const onSubmit = async (values: NewEntityTypeFormValues) => {
    await handleSubmit(values);
    form.reset();
  };

  return (
    <Popover>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="my-2 w-full lg:min-w-xl" align="end">
        <h1>Create a new Entity type</h1>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 pt-4">
            {/* Names row */}
            <div className="flex flex-col gap-2 lg:flex-row">
              <FormField
                control={form.control}
                name="pluralName"
                render={({ field }) => (
                  <FormItem className="flex w-full flex-col">
                    <FormLabel>Plural Noun</FormLabel>
                    <FormDescription className="text-xs italic">
                      This will be used to label a collection of these entities
                    </FormDescription>
                    <div className="flex items-center gap-2">
                      <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center rounded-md bg-primary/10">
                        <FormField
                          control={form.control}
                          name="icon"
                          render={({ field }) => {
                            return <IconSelector onSelect={field.onChange} icon={field.value} />;
                          }}
                        />
                      </div>
                      <FormControl>
                        <div className="flex h-auto flex-grow items-end">
                          <Input placeholder="e.g., Companies" {...field} className="flex-1" />
                        </div>
                      </FormControl>
                    </div>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="singularName"
                render={({ field }) => (
                  <FormItem className="flex w-full flex-col">
                    <FormLabel>Singular Noun</FormLabel>
                    <FormDescription className="text-xs italic">
                      How we should label a single entity of this type
                    </FormDescription>
                    <FormControl>
                      <div className="flex h-auto flex-grow items-end">
                        <Input placeholder="e.g., Company" {...field} />
                      </div>
                    </FormControl>
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
                  <FormDescription className="text-xs italic">
                    A brief summary of what this entity type represents
                  </FormDescription>
                  <FormControl>
                    <Textarea
                      className={cn(
                        'max-h-72',
                        semanticGroup === SemanticGroup.Custom && 'border-amber-500/50',
                      )}
                      placeholder="Describe what this entity type represents..."
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <p className={cn(
                    'min-h-4 text-xs',
                    semanticGroup === SemanticGroup.Custom
                      ? 'text-amber-600 dark:text-amber-400'
                      : 'invisible',
                  )}>
                    Describing what this entity type represents helps the system understand its
                    purpose
                  </p>
                  <FormMessage />
                </FormItem>
              )}
            />

            {/* Category + Tags row */}
            <div className="flex flex-col gap-4 lg:flex-row lg:gap-3">
              <FormField
                control={form.control}
                name="semanticGroup"
                render={({ field }) => (
                  <FormItem className="w-full lg:w-48 lg:shrink-0">
                    <FormLabel>Category</FormLabel>
                    <FormDescription className="text-xs italic">
                      The domain this type belongs to
                    </FormDescription>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <FormControl>
                        <SelectTrigger
                          className="w-full"
                          onPointerDown={(e) => {
                            e.stopPropagation();
                          }}
                        >
                          <SelectValue>
                            {CATEGORY_LABELS[field.value as SemanticGroup]}
                          </SelectValue>
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent
                        onCloseAutoFocus={(e) => {
                          e.preventDefault();
                        }}
                      >
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
                  <FormItem className="w-full min-w-0 flex-1">
                    <FormLabel>Tags</FormLabel>
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

            <footer className="flex w-full justify-end gap-2 pt-4">
              <PopoverClose asChild>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    form.reset();
                  }}
                >
                  Cancel
                </Button>
              </PopoverClose>
              <Button type="submit">
                <Plus className="size-4" />
                <span>Create Entity Type</span>
              </Button>
            </footer>
          </form>
        </Form>
      </PopoverContent>
    </Popover>
  );
};
