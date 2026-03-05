'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';
import { WorkspacePlan, type Workspace } from '@/lib/types/workspace';
import { AvatarUploader } from '@/components/ui/AvatarUploader';
import { Button, CardContent, CardFooter, Input } from '@riven/ui';
import { Checkbox } from '@/components/ui/checkbox';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { CurrencySelector } from '@/components/ui/forms/currency/form-currency-picker';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { cn } from '@riven/utils';
import type { ClassNameProps } from '@riven/utils';
import { isValidCurrency } from '@/lib/util/utils';
import { zodResolver } from '@hookform/resolvers/zod';
import { Link, SquareArrowUpRight } from 'lucide-react';
import { FC } from 'react';
import { useForm, UseFormReturn } from 'react-hook-form';
import { z } from 'zod';
import { WorkspaceFormPreview } from './workspace-preview';

const WorkspaceDetailsFormSchema = z.object({
  displayName: z
    .string({ required_error: 'Display Name is required' })
    .min(3, 'Display Name is too short'),
  plan: z.nativeEnum(WorkspacePlan),
  defaultCurrency: z.string().min(3).max(3).refine(isValidCurrency),
  avatarUrl: z.string().url().optional(),
  isDefault: z.boolean(),
});

export type WorkspaceFormDetails = z.infer<typeof WorkspaceDetailsFormSchema>;

interface Props extends ClassNameProps {
  workspace?: Workspace;
  renderHeader: () => React.ReactNode;
  onSubmit: (values: WorkspaceFormDetails) => Promise<void>;
  setUploadedAvatar: (file: Blob | null) => void;
}

export const WorkspaceForm: FC<Props> = ({
  workspace,
  onSubmit,
  setUploadedAvatar,
  renderHeader,
  className,
}) => {
  const { data: user } = useProfile();

  const form: UseFormReturn<WorkspaceFormDetails> = useForm<WorkspaceFormDetails>({
    resolver: zodResolver(WorkspaceDetailsFormSchema),
    defaultValues: {
      displayName: workspace?.name || '',
      avatarUrl: workspace?.avatarUrl || undefined,
      isDefault: user?.memberships.length === 0,
      plan: workspace?.plan || WorkspacePlan.Free,
      defaultCurrency: workspace?.defaultCurrency?.currencyCode || 'AUD',
    },
  });

  const handleAvatarUpload = (file: Blob): void => {
    // Store transformed image ready for upload upon form submission
    setUploadedAvatar(file);
    // Set the avatar URL in the form state
    const avatarURL = URL.createObjectURL(file);
    form.setValue('avatarUrl', avatarURL);
    URL.revokeObjectURL(avatarURL); // Clean up the object URL
  };

  const handleAvatarRemoval = (): void => {
    setUploadedAvatar(null);
    form.setValue('avatarUrl', undefined);
  };

  return (
    <div className="flex min-h-[100dvh] flex-col p-2 md:flex-row">
      <section className={cn('w-full md:w-2/5', className)}>
        <header>{renderHeader()}</header>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)}>
            <section className="mr-8">
              <FormField
                control={form.control}
                name="isDefault"
                render={({ field }) => (
                  <FormItem className="mt-4 mb-2 flex flex-row items-center justify-end gap-2">
                    <FormControl>
                      <Checkbox
                        disabled={user?.memberships.length === 0}
                        checked={field.value}
                        onCheckedChange={(checked) => {
                          field.onChange(checked);
                        }}
                      />
                    </FormControl>
                    <FormLabel className="text-sm font-normal">Set as default workspace</FormLabel>
                  </FormItem>
                )}
              />
            </section>
            <CardContent className="pb-8">
              <h3 className="mb-1 text-lg font-semibold">Workspace Overview</h3>
              <p className="mb-2 text-sm text-muted-foreground">
                Provide some basic information about your workspace. You can update these details
                later in your workspace settings.
              </p>
              <div className="mb-8 flex flex-col space-y-6">
                <FormField
                  control={form.control}
                  name="avatarUrl"
                  render={() => (
                    <FormItem className="flex w-full flex-col lg:flex-row">
                      <FormLabel className="w-1/3">Workspace Avatar</FormLabel>
                      <AvatarUploader
                        onUpload={handleAvatarUpload}
                        imageURL={form.getValues('avatarUrl')}
                        onRemove={handleAvatarRemoval}
                        validation={{
                          maxSize: 5 * 1024 * 1024, // 5MB
                          allowedTypes: ['image/jpeg', 'image/png', 'image/webp'],
                          errorMessage:
                            'Please upload a valid image file (JPEG, PNG, WebP) under 5MB',
                        }}
                      />
                    </FormItem>
                  )}
                />
                <FormField
                  control={form.control}
                  name="displayName"
                  render={({ field }) => (
                    <>
                      <FormItem className="mt-2 flex w-full flex-col lg:flex-row">
                        <FormLabel className="w-1/3" htmlFor="displayName">
                          Workspace Name *
                        </FormLabel>
                        <div className="w-auto flex-grow">
                          <Input
                            id="displayName"
                            placeholder="My Workspace"
                            className="w-full"
                            {...field}
                            required
                          />
                          <FormMessage className="mt-2 ml-1 text-xs" />
                        </div>
                      </FormItem>
                    </>
                  )}
                />
                <FormField
                  control={form.control}
                  name="defaultCurrency"
                  render={({ field }) => (
                    <>
                      <FormItem className="mt-2 flex w-full flex-col lg:flex-row">
                        <FormLabel className="w-1/3" htmlFor="defaultCurrency">
                          Default Currency *
                        </FormLabel>
                        <CurrencySelector
                          className="w-auto flex-grow"
                          value={field.value}
                          onChange={(value) => {
                            field.onChange(value);
                          }}
                        />
                      </FormItem>
                      <FormMessage />
                    </>
                  )}
                />
                <FormField
                  control={form.control}
                  name="plan"
                  render={({ field }) => (
                    <>
                      <FormItem className="mt-2 flex w-full flex-col lg:flex-row">
                        <div className="mt-2 w-full lg:w-1/3">
                          <FormLabel className="w-1/3" htmlFor="plan">
                            Workspace Plan *
                          </FormLabel>
                          <Link
                            href={'/pricing'}
                            target="_blank"
                            className="text-content mt-2 flex h-fit w-fit items-center text-xs transition-opacity hover:opacity-50"
                          >
                            Pricing
                            <SquareArrowUpRight className="ml-1 h-3 w-3" />
                          </Link>
                        </div>
                        <Select defaultValue={field.value} onValueChange={field.onChange}>
                          <FormControl className="mt-2 flex w-auto flex-grow">
                            <SelectTrigger className="w-full lg:w-auto">
                              <SelectValue placeholder="Select a workspace plan" />
                            </SelectTrigger>
                          </FormControl>
                          <SelectContent>
                            <SelectItem value="FREE" className="flex justify-between">
                              <span>Free</span>
                              <span className="ml-2 text-xs text-muted-foreground">- $0/month</span>
                            </SelectItem>
                            <SelectItem value="STARTUP">
                              <span>Startup</span>
                              <span className="ml-2 text-xs text-muted-foreground">
                                - $10/month
                              </span>
                            </SelectItem>
                            <SelectItem value="SCALE">
                              <span>Scale</span>
                              <span className="ml-2 text-xs text-muted-foreground">
                                - $25/month
                              </span>
                            </SelectItem>
                            <SelectItem value="ENTERPRISE">
                              <span>Enterprise</span>
                              <span className="ml-2 text-xs text-muted-foreground">
                                - Custom pricing
                              </span>
                            </SelectItem>
                          </SelectContent>
                        </Select>
                      </FormItem>
                      <FormMessage />
                    </>
                  )}
                />
              </div>
            </CardContent>
            <CardFooter className="mt-4 flex justify-end border-t py-1">
              <Button type="submit" size={'sm'} className="cursor-pointer">
                Save
              </Button>
            </CardFooter>
          </form>
        </Form>
      </section>
      <section className="relative flex flex-grow flex-col rounded-sm bg-accent">
        <div className="sticky top-8">
          <WorkspaceFormPreview data={form.watch()} />
        </div>
      </section>
    </div>
  );
};
