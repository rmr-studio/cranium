'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { useUpdateProfileMutation } from '@/components/feature-modules/user/hooks/mutation/use-update-profile-mutation';
import { AppearanceSection } from '@/components/feature-modules/user/components/account-settings-appearance';
import { AccountSettingsSkeleton } from '@/components/feature-modules/user/components/account-settings-skeleton';
import { AvatarUploader } from '@/components/ui/avatar-uploader';
import { AVATAR_VALIDATION } from '@/lib/util/avatar/avatar-validation';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { Separator } from '@riven/ui/separator';
import { Info } from 'lucide-react';
import { useCallback, useEffect, useMemo, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import { joinName, splitName } from '@/lib/util/utils';
import { bustImageCache } from '@/lib/util/avatar/bust-image-cache';
import type { SaveUserRequest } from '@/lib/types/user';

export const accountSettingsSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(50),
  lastName: z.string().max(50).optional().default(''),
});

export type AccountFormValues = z.infer<typeof accountSettingsSchema>;

export function AccountSettings() {
  const { data: user, isLoading, isLoadingAuth } = useProfile();
  const { mutateAsync: updateProfile, isPending } = useUpdateProfileMutation();
  const [uploadedAvatar, setUploadedAvatar] = useState<Blob | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | undefined>(undefined);
  const [avatarRemoved, setAvatarRemoved] = useState(false);

  const defaultValues = useMemo(() => {
    if (!user) return { firstName: '', lastName: '' };
    const { firstName, lastName } = splitName(user.name);
    return { firstName, lastName };
  }, [user]);

  const form = useForm<AccountFormValues>({
    resolver: zodResolver(accountSettingsSchema),
    defaultValues,
  });

  // Reset form when user data first arrives
  useEffect(() => {
    if (user) {
      const { firstName, lastName } = splitName(user.name);
      form.reset({ firstName, lastName });
      setAvatarPreviewUrl(user.avatarUrl || undefined);
    }
  }, [user?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleAvatarUpload = useCallback((file: Blob) => {
    setUploadedAvatar(file);
    setAvatarRemoved(false);
    const url = URL.createObjectURL(file);
    setAvatarPreviewUrl(url);
  }, []);

  const handleAvatarRemove = useCallback(() => {
    setUploadedAvatar(null);
    setAvatarPreviewUrl(undefined);
    setAvatarRemoved(true);
  }, []);

  const handleSubmit = async (values: AccountFormValues) => {
    if (!user) return;

    const request: SaveUserRequest = {
      name: joinName(values.firstName, values.lastName),
      email: user.email,
      phone: user.phone,
      defaultWorkspaceId: user.defaultWorkspaceId,
      onboardingCompletedAt: user.onboardingCompletedAt,
      removeAvatar: avatarRemoved,
    };

    const result = await updateProfile({ user: request, avatar: uploadedAvatar });

    // Update form from response (Issue 13A pattern)
    const { firstName, lastName } = splitName(result.name);
    form.reset({ firstName, lastName });
    setAvatarPreviewUrl(bustImageCache(result.avatarUrl));
    setUploadedAvatar(null);
    setAvatarRemoved(false);
  };

  if (isLoadingAuth || isLoading) {
    return <AccountSettingsSkeleton />;
  }

  if (!user) return null;

  return (
    <div className="mx-auto w-full max-w-2xl space-y-10 px-6 py-10">
      {/* Profile Section */}
      <section className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">Profile</h1>
          <p className="text-sm text-muted-foreground">Manage your personal details</p>
        </div>

        <div className="flex items-center gap-3 rounded-lg border border-border/50 bg-muted/30 px-4 py-3">
          <Info className="size-4 shrink-0 text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            Changes to your profile will apply to all of your workspaces.
          </p>
        </div>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-8">
            <AvatarUploader
              onUpload={handleAvatarUpload}
              onRemove={avatarPreviewUrl ? handleAvatarRemove : undefined}
              imageURL={avatarPreviewUrl}
              title="Profile Picture"
              validation={AVATAR_VALIDATION}
            />

            <div className="grid grid-cols-2 gap-4">
              <FormField
                control={form.control}
                name="firstName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>First Name</FormLabel>
                    <FormControl>
                      <Input placeholder="First name" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="lastName"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Last Name</FormLabel>
                    <FormControl>
                      <Input placeholder="Last name" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <FormItem>
              <FormLabel>Primary email address</FormLabel>
              <div className="flex items-center gap-2">
                <Input value={user.email} disabled className="flex-1" />
              </div>
            </FormItem>

            <div className="flex justify-end">
              <Button type="submit" size="sm" disabled={isPending || (!form.formState.isDirty && !uploadedAvatar && !avatarRemoved)}>
                {isPending ? 'Saving...' : 'Save changes'}
              </Button>
            </div>
          </form>
        </Form>
      </section>

      <Separator />

      {/* Appearance Section */}
      <AppearanceSection />
    </div>
  );
}
