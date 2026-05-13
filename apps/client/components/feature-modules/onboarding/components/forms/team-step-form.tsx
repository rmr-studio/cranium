'use client';

import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormMessage,
} from '@/components/ui/form';
import { Input } from '@cranium/ui/input';
import { Button } from '@cranium/ui/button';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { WorkspaceRoles } from '@/lib/types/workspace';
import { zodResolver } from '@hookform/resolvers/zod';
import { X } from 'lucide-react';
import { FC, useEffect, useState } from 'react';
import { useForm } from 'react-hook-form';
import { z } from 'zod';
import {
  useOnboardStoreApi,
  useOnboardFormControls,
} from '@/components/feature-modules/onboarding/hooks/use-onboard-store';

// ─── Constants and helpers ────────────────────────────────────────────────────

export const INVITE_ROLES = [WorkspaceRoles.Admin, WorkspaceRoles.Member] as const;

export const INVITE_SOFT_CAP = 10;

export interface PendingInvite {
  email: string;
  role: WorkspaceRoles;
}

export const inviteEntrySchema = z.object({
  email: z.string().email('Enter a valid email address'),
  role: z.enum([WorkspaceRoles.Admin, WorkspaceRoles.Member]),
});

export type InviteEntryData = z.infer<typeof inviteEntrySchema>;

export function isDuplicateEmail(email: string, invites: PendingInvite[]): boolean {
  return invites.some((invite) => invite.email.toLowerCase() === email.toLowerCase());
}

// ─── Live data shape ──────────────────────────────────────────────────────────

interface TeamLiveData {
  invites: PendingInvite[];
}

// ─── Role label map ───────────────────────────────────────────────────────────

const ROLE_LABELS: Record<WorkspaceRoles.Admin | WorkspaceRoles.Member, string> = {
  [WorkspaceRoles.Admin]: 'Admin',
  [WorkspaceRoles.Member]: 'Member',
};

// ─── Component ────────────────────────────────────────────────────────────────

export const TeamStepForm: FC = () => {
  const storeApi = useOnboardStoreApi();
  const { setLiveData, registerFormTrigger, clearFormTrigger } = useOnboardFormControls();
  const [restoredData] = useState(
    () => storeApi.getState().liveData['team'] as TeamLiveData | undefined,
  );

  const [invites, setInvites] = useState<PendingInvite[]>(restoredData?.invites ?? []);

  const form = useForm<InviteEntryData>({
    resolver: zodResolver(inviteEntrySchema),
    defaultValues: {
      email: '',
      role: WorkspaceRoles.Member,
    },
  });

  // Team step is optional — trigger always resolves true
  useEffect(() => {
    registerFormTrigger(async () => true);
    return () => clearFormTrigger();
  }, [registerFormTrigger, clearFormTrigger]);

  // Sync invite list changes to liveData
  useEffect(() => {
    setLiveData('team', { invites });
  }, [invites, setLiveData]);

  const handleAdd = async () => {
    const valid = await form.trigger();
    if (!valid) return;

    const values = form.getValues();

    if (isDuplicateEmail(values.email, invites)) {
      form.setError('email', { message: 'This email is already in your invite list' });
      return;
    }

    if (invites.length >= INVITE_SOFT_CAP) {
      form.setError('email', { message: 'You can invite more from settings after setup' });
      return;
    }

    setInvites((prev) => [...prev, { email: values.email, role: values.role }]);
    form.reset({ email: '', role: WorkspaceRoles.Member });
  };

  const handleRemove = (email: string) => {
    setInvites((prev) => prev.filter((invite) => invite.email !== email));
  };

  const handleRoleChange = (email: string, newRole: WorkspaceRoles) => {
    setInvites((prev) =>
      prev.map((invite) => (invite.email === email ? { ...invite, role: newRole } : invite)),
    );
  };

  return (
    <Form {...form}>
      <form
        onSubmit={(e) => e.preventDefault()}
        className="flex flex-col gap-4"
      >
        {/* Input row: email + role + add button */}
        <div className="flex items-start gap-2">
          <FormField
            control={form.control}
            name="email"
            render={({ field }) => (
              <FormItem className="flex-1">
                <FormControl>
                  <Input placeholder="colleague@company.com" {...field} />
                </FormControl>
                <FormMessage />
              </FormItem>
            )}
          />

          <FormField
            control={form.control}
            name="role"
            render={({ field }) => (
              <FormItem>
                <Select value={field.value} onValueChange={field.onChange}>
                  <FormControl>
                    <SelectTrigger className="w-28">
                      <SelectValue />
                    </SelectTrigger>
                  </FormControl>
                  <SelectContent>
                    {INVITE_ROLES.map((role) => (
                      <SelectItem key={role} value={role}>
                        {ROLE_LABELS[role]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </FormItem>
            )}
          />

          <Button type="button" variant="outline" onClick={handleAdd}>
            Add
          </Button>
        </div>

        {/* Invite list */}
        {invites.length > 0 && (
          <div className="flex flex-col gap-2">
            {invites.map((invite) => (
              <div key={invite.email} className="flex items-center gap-3">
                <span className="flex-1 truncate text-sm font-medium">{invite.email}</span>

                <Select
                  value={invite.role}
                  onValueChange={(value) => handleRoleChange(invite.email, value as WorkspaceRoles)}
                >
                  <SelectTrigger className="w-28" size="sm">
                    <SelectValue />
                  </SelectTrigger>
                  <SelectContent>
                    {INVITE_ROLES.map((role) => (
                      <SelectItem key={role} value={role}>
                        {ROLE_LABELS[role]}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>

                <Button
                  type="button"
                  variant="ghost"
                  size="icon"
                  className="size-8 shrink-0"
                  onClick={() => handleRemove(invite.email)}
                  aria-label={`Remove ${invite.email}`}
                >
                  <X className="size-4" />
                </Button>
              </div>
            ))}
          </div>
        )}
      </form>
    </Form>
  );
};
