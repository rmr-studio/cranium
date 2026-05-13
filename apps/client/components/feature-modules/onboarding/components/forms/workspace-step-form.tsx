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
import { WorkspacePlan } from '@/lib/types/workspace';
import { zodResolver } from '@hookform/resolvers/zod';
import { cn } from '@/lib/util/utils';
import { FC, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  useOnboardStoreApi,
  useOnboardFormControls,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

export const workspaceStepSchema = z.object({
  displayName: z
    .string({ required_error: 'Workspace name is required' })
    .trim()
    .min(3, 'Must be at least 3 characters'),
  plan: z.nativeEnum(WorkspacePlan, {
    required_error: 'Please select a plan',
    invalid_type_error: 'Please select a plan',
  }),
});

export type WorkspaceStepData = z.infer<typeof workspaceStepSchema>;

interface WorkspaceLiveData {
  displayName?: string;
  plan?: WorkspacePlan;
  avatarPreviewUrl?: string;
}

const PLAN_OPTIONS: {
  plan: WorkspacePlan;
  label: string;
  price: string;
  description: string;
}[] = [
  {
    plan: WorkspacePlan.Free,
    label: 'Free',
    price: '$0/mo',
    description: 'For individuals and small projects',
  },
  {
    plan: WorkspacePlan.Startup,
    label: 'Startup',
    price: '$29/mo',
    description: 'For growing teams',
  },
  {
    plan: WorkspacePlan.Scale,
    label: 'Scale',
    price: '$79/mo',
    description: 'For scaling businesses',
  },
  {
    plan: WorkspacePlan.Enterprise,
    label: 'Enterprise',
    price: 'Custom',
    description: 'For large organisations',
  },
];

export const WorkspaceStepForm: FC = () => {
  const storeApi = useOnboardStoreApi();
  const { setLiveData, registerFormTrigger, clearFormTrigger } = useOnboardFormControls();
  const [restoredData] = useState(
    () => storeApi.getState().liveData['workspace'] as WorkspaceLiveData | undefined,
  );

  const [avatarBlob, setAvatarBlob] = useState<Blob | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | undefined>(
    restoredData?.avatarPreviewUrl,
  );

  const form = useForm<WorkspaceStepData>({
    resolver: zodResolver(workspaceStepSchema),
    defaultValues: {
      displayName: restoredData?.displayName ?? '',
      plan: restoredData?.plan ?? undefined,
    },
  });

  // Register form trigger on mount so nav controls can call form.trigger()
  useEffect(() => {
    registerFormTrigger(() => form.trigger());
    return () => clearFormTrigger();
  }, [form, registerFormTrigger, clearFormTrigger]);

  // Fire initial liveData so preview shows pre-populated data immediately
  useEffect(() => {
    const values = form.getValues();
    setLiveData('workspace', { ...values, avatarPreviewUrl });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Watch subscription: keep liveData in sync as user types
  useEffect(() => {
    const sub = form.watch((values) => {
      setLiveData('workspace', { ...values, avatarPreviewUrl });
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
    storeApi.getState().setWorkspaceAvatarBlob(file);
    setAvatarPreviewUrl(url);
    setLiveData('workspace', { ...form.getValues(), avatarPreviewUrl: url });
  };

  const handleAvatarRemove = () => {
    if (avatarPreviewUrl && avatarPreviewUrl.startsWith('blob:')) {
      URL.revokeObjectURL(avatarPreviewUrl);
    }
    setAvatarBlob(null);
    storeApi.getState().setWorkspaceAvatarBlob(null);
    setAvatarPreviewUrl(undefined);
    setLiveData('workspace', { ...form.getValues(), avatarPreviewUrl: undefined });
  };

  // avatarBlob kept as local state for managing object URL lifecycle
  void avatarBlob;

  const selectedPlan = form.watch('plan');

  return (
    <Form {...form}>
      <form className="flex flex-col gap-6">
        <AvatarUploader
          onUpload={handleAvatarUpload}
          onRemove={avatarPreviewUrl ? handleAvatarRemove : undefined}
          imageURL={avatarPreviewUrl}
          title="Workspace logo"
          validation={{
            maxSize: 5 * 1024 * 1024,
            allowedTypes: ['image/jpeg', 'image/png', 'image/webp'],
            errorMessage: 'Please upload a JPEG, PNG, or WebP under 5MB.',
          }}
        />

        <FormField
          control={form.control}
          name="displayName"
          render={({ field }) => (
            <FormItem>
              <FormLabel>Workspace name</FormLabel>
              <FormControl>
                <Input placeholder="Your company or team name" {...field} />
              </FormControl>
              <FormMessage />
            </FormItem>
          )}
        />

        <FormField
          control={form.control}
          name="plan"
          render={() => (
            <FormItem>
              <FormLabel>Plan</FormLabel>
              <div className="grid grid-cols-2 gap-3">
                {PLAN_OPTIONS.map(({ plan, label, price, description }) => (
                  <button
                    key={plan}
                    type="button"
                    onClick={() => form.setValue('plan', plan, { shouldValidate: false })}
                    className={cn(
                      'flex flex-col items-start gap-1 rounded-lg border p-4 text-left transition-colors',
                      selectedPlan === plan
                        ? 'border-primary bg-primary/5 ring-2 ring-primary'
                        : 'border-border hover:border-muted-foreground/40',
                    )}
                  >
                    <span className="text-sm font-semibold">{label}</span>
                    <span className="text-primary text-sm font-medium">{price}</span>
                    <span className="text-muted-foreground text-xs">{description}</span>
                  </button>
                ))}
              </div>
              <FormMessage />
            </FormItem>
          )}
        />
      </form>
    </Form>
  );
};
