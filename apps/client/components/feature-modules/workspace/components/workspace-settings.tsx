'use client';

import { useDeleteWorkspaceMutation } from '@/components/feature-modules/workspace/hooks/mutation/use-delete-workspace-mutation';
import { useSaveWorkspaceMutation } from '@/components/feature-modules/workspace/hooks/mutation/use-save-workspace-mutation';
import { useWorkspace } from '@/components/feature-modules/workspace/hooks/query/use-workspace';
import { AvatarUploader } from '@/components/ui/avatar-uploader';
import { DestructiveConfirmDialog } from '@/components/ui/destructive-confirm-dialog';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { Skeleton } from '@/components/ui/skeleton';
import type { SaveWorkspaceRequest } from '@/lib/types/workspace';
import { AVATAR_VALIDATION } from '@/lib/util/avatar/avatar-validation';
import { bustImageCache } from '@/lib/util/avatar/bust-image-cache';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@cranium/ui/button';
import { Input } from '@cranium/ui/input';
import { Separator } from '@cranium/ui/separator';
import { useQueryClient } from '@tanstack/react-query';
import { Trash2 } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { useCallback, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';

export const workspaceSettingsSchema = z.object({
  name: z
    .string({ required_error: 'Workspace name is required' })
    .min(3, 'Name must be at least 3 characters')
    .max(100),
});

export type WorkspaceSettingsFormValues = z.infer<typeof workspaceSettingsSchema>;

export function WorkspaceSettings() {
  const { data: workspace, isLoading, isLoadingAuth } = useWorkspace();
  const queryClient = useQueryClient();
  const router = useRouter();

  const [uploadedAvatar, setUploadedAvatar] = useState<Blob | null>(null);
  const [avatarPreviewUrl, setAvatarPreviewUrl] = useState<string | undefined>(undefined);
  const [avatarRemoved, setAvatarRemoved] = useState(false);
  const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);

  const { mutateAsync: saveWorkspace, isPending: isSaving } = useSaveWorkspaceMutation({
    onSuccess: (updated) => {
      form.reset({ name: updated.name });
      setAvatarPreviewUrl(bustImageCache(updated.avatarUrl));
      setUploadedAvatar(null);
      setAvatarRemoved(false);
      queryClient.invalidateQueries({ queryKey: ['workspace', updated.id] });
    },
  });

  const { mutateAsync: deleteWorkspace, isPending: isDeleting } = useDeleteWorkspaceMutation({
    onSuccess: () => {
      router.push('/dashboard');
    },
  });

  const form = useForm<WorkspaceSettingsFormValues>({
    resolver: zodResolver(workspaceSettingsSchema),
    defaultValues: { name: '' },
  });

  // Initialize form when workspace data arrives
  useEffect(() => {
    if (workspace) {
      form.reset({ name: workspace.name });
      setAvatarPreviewUrl(workspace.avatarUrl);
    }
  }, [workspace?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleAvatarUpload = useCallback((file: Blob) => {
    setUploadedAvatar(file);
    setAvatarRemoved(false);
    setAvatarPreviewUrl((prev) => {
      if (prev?.startsWith('blob:')) URL.revokeObjectURL(prev);
      return URL.createObjectURL(file);
    });
  }, []);

  const handleAvatarRemove = useCallback(() => {
    setUploadedAvatar(null);
    setAvatarPreviewUrl(undefined);
    setAvatarRemoved(true);
  }, []);

  const handleSubmit = async (values: WorkspaceSettingsFormValues) => {
    if (!workspace) return;

    const request: SaveWorkspaceRequest = {
      id: workspace.id,
      name: values.name,
      plan: workspace.plan,
      defaultCurrency: workspace.defaultCurrency?.currencyCode ?? 'USD',
      isDefault: false,
      removeAvatar: avatarRemoved,
    };

    await saveWorkspace({ workspace: request, avatar: uploadedAvatar });
  };

  const handleDelete = async () => {
    if (!workspace) return;
    await deleteWorkspace(workspace.id);
  };

  if (isLoadingAuth || isLoading) {
    return <WorkspaceSettingsSkeleton />;
  }

  if (!workspace) return null;

  return (
    <div className="mx-auto w-full max-w-2xl space-y-10 px-6 py-10">
      {/* General Section */}
      <section className="space-y-6">
        <div>
          <h1 className="text-2xl font-semibold tracking-tight">General</h1>
          <p className="text-sm text-muted-foreground">
            Change the settings for your current workspace
          </p>
        </div>

        <Form {...form}>
          <form onSubmit={form.handleSubmit(handleSubmit)} className="space-y-8">
            <AvatarUploader
              onUpload={handleAvatarUpload}
              onRemove={avatarPreviewUrl ? handleAvatarRemove : undefined}
              imageURL={avatarPreviewUrl}
              title="Workspace logo"
              validation={AVATAR_VALIDATION}
            />

            <div className="grid grid-cols-1 gap-4">
              <FormField
                control={form.control}
                name="name"
                render={({ field }) => (
                  <FormItem>
                    <FormLabel>Name</FormLabel>
                    <FormControl>
                      <Input placeholder="Workspace name" {...field} />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <div className="flex justify-end">
              <Button
                type="submit"
                size="sm"
                disabled={
                  isSaving || (!form.formState.isDirty && !uploadedAvatar && !avatarRemoved)
                }
              >
                {isSaving ? 'Saving...' : 'Save changes'}
              </Button>
            </div>
          </form>
        </Form>
      </section>

      <Separator />

      {/* Danger Zone */}
      <section className="space-y-4">
        <h2 className="text-lg font-semibold text-destructive">Danger zone</h2>
        <div className="flex items-center justify-between rounded-lg border border-destructive/30 bg-destructive/5 px-4 py-4">
          <div>
            <h3 className="text-sm font-medium">Delete workspace</h3>
            <p className="text-sm text-muted-foreground">
              Once deleted, your workspace cannot be recovered
            </p>
          </div>
          <Button variant="destructive" size="sm" onClick={() => setDeleteDialogOpen(true)}>
            <Trash2 className="size-3.5" />
            Delete workspace
          </Button>
        </div>
      </section>

      <DestructiveConfirmDialog
        open={deleteDialogOpen}
        onOpenChange={setDeleteDialogOpen}
        title="Delete workspace"
        description={`This action cannot be undone. This will permanently delete the "${workspace.name}" workspace and all of its data.`}
        confirmLabel="Delete workspace"
        confirmValue={workspace.name}
        onConfirm={handleDelete}
        isPending={isDeleting}
      />
    </div>
  );
}

function WorkspaceSettingsSkeleton() {
  return (
    <div className="mx-auto w-full max-w-2xl space-y-10 px-6 py-10">
      <div className="space-y-6">
        <div>
          <Skeleton className="mb-2 h-7 w-32" />
          <Skeleton className="h-4 w-64" />
        </div>
        <div className="flex items-center gap-4">
          <Skeleton className="size-18 rounded-full" />
          <div className="space-y-2">
            <Skeleton className="h-4 w-24" />
            <Skeleton className="h-8 w-20 rounded-md" />
          </div>
        </div>
        <Skeleton className="h-16" />
      </div>
    </div>
  );
}
