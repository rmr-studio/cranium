'use client';

import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@cranium/ui/input';
import { AvatarUploader } from '@/components/ui/avatar-uploader';
import { AVATAR_VALIDATION } from '@/lib/util/avatar/avatar-validation';
import { useAuth } from '@/components/provider/auth-context';
import { zodResolver } from '@hookform/resolvers/zod';
import { FC, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  useOnboardStoreApi,
  useOnboardFormControls,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

export const profileStepSchema = z.object({
  displayName: z
    .string({ required_error: 'Display name is required' })
    .trim()
    .min(3, 'Must be at least 3 characters'),
});

export type ProfileStepData = z.infer<typeof profileStepSchema>;

interface ProfileLiveData {
  displayName?: string;
  avatarPreviewUrl?: string;
}

export const ProfileStepForm: FC = () => {
  const { user } = useAuth();
  const storeApi = useOnboardStoreApi();
  const { setLiveData, registerFormTrigger, clearFormTrigger } = useOnboardFormControls();
  const [restoredData] = useState(
    () => storeApi.getState().liveData['profile'] as ProfileLiveData | undefined,
  );

  const [avatarBlob, setAvatarBlob] = useState<Blob | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | undefined>(
    restoredData?.avatarPreviewUrl,
  );

  const form = useForm<ProfileStepData>({
    resolver: zodResolver(profileStepSchema),
    defaultValues: {
      displayName:
        restoredData?.displayName ??
        (user?.metadata?.full_name as string | undefined) ??
        (user?.metadata?.name as string | undefined) ??
        '',
    },
  });

  // Sync displayName from user metadata if form is still empty
  useEffect(() => {
    const current = form.getValues('displayName');
    if (current) return;
    const metaName =
      (user?.metadata?.full_name as string | undefined) ??
      (user?.metadata?.name as string | undefined);
    if (metaName) {
      form.setValue('displayName', metaName);
      setLiveData('profile', { displayName: metaName, avatarPreviewUrl });
    }
  }, [user, form, setLiveData, avatarPreviewUrl]);

  // Register form trigger on mount so nav controls can call form.trigger()
  useEffect(() => {
    registerFormTrigger(() => form.trigger());
    return () => clearFormTrigger();
  }, [form, registerFormTrigger, clearFormTrigger]);

  // Fire initial liveData so preview shows pre-populated data immediately
  useEffect(() => {
    const values = form.getValues();
    setLiveData('profile', { ...values, avatarPreviewUrl });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Watch subscription: keep liveData in sync as user types
  useEffect(() => {
    const sub = form.watch((values) => {
      setLiveData('profile', { ...values, avatarPreviewUrl });
    });
    return () => sub.unsubscribe();
  }, [form, setLiveData, avatarPreviewUrl]);

  // Revoke object URL on unmount to prevent memory leaks
  useEffect(() => {
    return () => {
      if (avatarPreviewUrl && avatarPreviewUrl.startsWith('blob:')) {
        URL.revokeObjectURL(avatarPreviewUrl);
      }
    };
  }, [avatarPreviewUrl]);

  const handleAvatarUpload = (file: Blob) => {
    if (avatarPreviewUrl && avatarPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(avatarPreviewUrl);
    }
    const url = URL.createObjectURL(file);
    setAvatarBlob(file);
    storeApi.getState().setProfileAvatarBlob(file);
    setAvatarPreviewUrl(url);
    setLiveData('profile', { ...form.getValues(), avatarPreviewUrl: url });
  };

  const handleAvatarRemove = () => {
    if (avatarPreviewUrl && avatarPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(avatarPreviewUrl);
    }
    setAvatarBlob(null);
    storeApi.getState().setProfileAvatarBlob(null);
    setAvatarPreviewUrl(undefined);
    setLiveData('profile', { ...form.getValues(), avatarPreviewUrl: undefined });
  };

  // avatarBlob kept as local state for managing object URL lifecycle
  void avatarBlob;

  return (
    <Form {...form}>
      <form className="flex flex-col gap-6">
        <AvatarUploader
          onUpload={handleAvatarUpload}
          onRemove={avatarPreviewUrl ? handleAvatarRemove : undefined}
          imageURL={avatarPreviewUrl}
          title="Profile picture"
          validation={AVATAR_VALIDATION}
        />

        <FormField
          control={form.control}
          name="displayName"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Display name</FormLabel>
              <FormControl>
                <Input placeholder="Your name" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />
      </form>
    </Form>
  );
};
