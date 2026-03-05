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
import type { ChildNodeProps } from '@riven/utils';
import { EntityType } from '@/lib/types/entity';
import { toKeyCase } from '@riven/utils';
import { PopoverClose } from '@radix-ui/react-popover';
import { Blocks, Info, Plus, Workflow } from 'lucide-react';
import { FC, useEffect } from 'react';
import {
  NewEntityTypeFormValues,
  useNewEntityTypeForm,
} from '../../../hooks/form/type/use-new-type-form';

interface Props extends ChildNodeProps {
  entityTypes?: EntityType[];
  workspaceId: string;
}

export const NewEntityTypeForm: FC<Props> = ({ entityTypes = [], workspaceId, children }) => {
  const { form, keyManuallyEdited, setKeyManuallyEdited, handleSubmit } =
    useNewEntityTypeForm(workspaceId);

  // Watch the pluralName field for dynamic title and key generation
  const pluralName = form.watch('pluralName');

  // Auto-generate key from pluralName
  useEffect(() => {
    if (!keyManuallyEdited && pluralName) {
      const generatedKey = toKeyCase(pluralName);
      form.setValue('key', generatedKey, { shouldValidate: false });
    }
  }, [pluralName, keyManuallyEdited]);

  const onSubmit = async (values: NewEntityTypeFormValues) => {
    await handleSubmit(values);
    form.reset();
    setKeyManuallyEdited(false);
  };

  return (
    <Popover>
      <PopoverTrigger asChild>{children}</PopoverTrigger>
      <PopoverContent className="my-2 w-full lg:min-w-3xl" align="end">
        <h1>Create a new Entity type</h1>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4 pt-4">
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

              {/* Singular Noun */}
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
            <div className="flex flex-col gap-2 lg:flex-row">
              <FormField
                control={form.control}
                name="key"
                render={({ field }) => (
                  <FormItem className="w-full">
                    <FormLabel className="flex items-center gap-1">
                      Identifier
                      <Info className="h-3 w-3 text-muted-foreground" />
                    </FormLabel>
                    <div className="flex items-center gap-2">
                      <FormControl>
                        <Input
                          placeholder="e.g., companies"
                          {...field}
                          onChange={(e) => {
                            field.onChange(e);
                            setKeyManuallyEdited(true);
                          }}
                          className="flex-1"
                        />
                      </FormControl>
                    </div>
                    <p className="text-xs text-muted-foreground">
                      <span className="font-medium">Important:</span> Once an object is created the
                      slug cannot be changed.
                    </p>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="type"
                render={({ field }) => (
                  <FormItem className="flex flex-col items-start gap-2">
                    <FormLabel className="flex items-center gap-1">Type</FormLabel>
                    <Select onValueChange={field.onChange} value={field.value}>
                      <FormControl>
                        <SelectTrigger
                          onPointerDown={(e) => {
                            // Prevent the click from closing the popover
                            e.stopPropagation();
                          }}
                          className="flex w-52 overflow-hidden pl-0"
                        >
                          <div className="flex h-9 w-9 flex-shrink-0 items-center justify-center bg-primary/5 shadow-sm">
                            {field.value === 'STANDARD' ? (
                              <Blocks className="h-4 w-4 text-primary" />
                            ) : (
                              <Workflow className="h-4 w-4 text-primary" />
                            )}
                          </div>
                          <SelectValue>
                            {field.value === 'STANDARD' ? 'Standard' : 'Relationship'}
                          </SelectValue>
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent
                        align="end"
                        className="max-w-sm"
                        onCloseAutoFocus={(e) => {
                          // Prevent focus returning to popover which would close it
                          e.preventDefault();
                        }}
                      >
                        <SelectItem value="STANDARD" textValue="Standard">
                          <div className="flex items-center">
                            <Blocks className="mr-3 size-4.5" />
                            <div>
                              <h3 className="mb-1">Standard</h3>
                              <div className="max-w-2xs text-xs leading-tight text-muted-foreground">
                                Standard entities can exist standalone or link to other entities.
                              </div>
                            </div>
                          </div>
                        </SelectItem>
                        <SelectItem value="RELATIONSHIP" textValue="Relationship">
                          <div className="flex items-center">
                            <Workflow className="mr-3 size-4.5" />
                            <div>
                              <h3 className="mb-1">Relationship</h3>
                              <div className="max-w-2xs text-xs leading-tight text-muted-foreground">
                                Relationship Entities are designed to represent connections between
                                other entities.
                              </div>
                            </div>
                          </div>
                        </SelectItem>
                      </SelectContent>
                    </Select>

                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>
            <FormField
              control={form.control}
              name="description"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Description</FormLabel>
                  <FormControl>
                    <Textarea
                      className="max-h-72"
                      placeholder="Describe what this entity type represents..."
                      rows={3}
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />

            <footer className="flex w-full justify-end gap-2 pt-4">
              <PopoverClose asChild>
                <Button
                  type="button"
                  variant="outline"
                  onClick={() => {
                    form.reset();
                    setKeyManuallyEdited(false);
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
