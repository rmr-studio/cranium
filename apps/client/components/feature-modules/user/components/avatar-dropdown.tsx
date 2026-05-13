'use client';

import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { isAuthError } from '@/lib/auth';
import { getAuthErrorMessage } from '@/lib/auth/error-messages';
import type { User } from '@/lib/types/user';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@cranium/ui/dropdown-menu';
import { getInitials } from '@cranium/utils';

import {
  AppWindowMac,
  ArrowLeftToLine,
  Cog,
  Plug,
  ReceiptText,
  SlidersHorizontal,
  User as UserIcon,
} from 'lucide-react';
import { useRouter } from 'next/navigation';
import { FC } from 'react';
import { toast } from 'sonner';
import { useAuth } from '../../../provider/auth-context';

interface Props {
  user: User;
  workspaceId?: string;
}

export const UserProfileDropdown: FC<Props> = ({ user, workspaceId }) => {
  const { name } = user;
  const { signOut } = useAuth();
  const router = useRouter();

  const workspace = user.memberships.find((m) => m.workspace?.id === workspaceId)?.workspace;

  const handleLogout = async () => {
    try {
      await signOut();
      router.push('/');
    } catch (error) {
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        console.error('Logout failed:', error);
      }
    }
  };

  return (
    <DropdownMenu modal={false}>
      <DropdownMenuTrigger asChild>
        <Avatar className="size-9 cursor-pointer rounded-sm border border-border/50">
          {user.avatarUrl && <AvatarImage src={user.avatarUrl} alt={name} />}
          <AvatarFallback className="rounded-sm border border-border/50 bg-muted/50">
            {getInitials(name)}
          </AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="mx-4 mt-1 px-2">
        <DropdownMenuLabel>
          <UserIcon />
          <span className="ml-2 text-xs font-semibold">{user.email}</span>
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuLabel className="text-xs text-content/70">Account</DropdownMenuLabel>
          <DropdownMenuItem onSelect={() => router.push('/dashboard/settings')}>
            <SlidersHorizontal />
            <span className="ml-2 text-xs text-content">Preferences</span>
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        {workspace && (
          <>
            <DropdownMenuGroup>
              <DropdownMenuLabel className="text-xs text-content/70">
                Workspace ({workspace.name})
              </DropdownMenuLabel>
              <DropdownMenuItem
                onSelect={() => router.push(`/dashboard/workspace/${workspace.id}`)}
              >
                <AppWindowMac />
                <span className="ml-2 text-xs text-content">Dashboard</span>
              </DropdownMenuItem>

              <DropdownMenuItem
                onSelect={() => router.push(`/dashboard/workspace/${workspace.id}/invoices`)}
              >
                <ReceiptText />
                <span className="ml-2 text-xs text-content">Invoices</span>
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() => router.push(`/dashboard/workspace/${workspace.id}/settings`)}
              >
                <Cog />
                <span className="ml-2 text-xs text-content">Settings</span>
              </DropdownMenuItem>
              <DropdownMenuItem
                onSelect={() => router.push(`/dashboard/workspace/${workspace.id}/integrations`)}
              >
                <Plug />
                <span className="ml-2 text-xs text-content">Connections</span>
              </DropdownMenuItem>
            </DropdownMenuGroup>

            <DropdownMenuSeparator />
          </>
        )}
        <DropdownMenuGroup>
          <DropdownMenuItem onSelect={async () => handleLogout()}>
            <ArrowLeftToLine />
            <span className="ml-2 text-xs text-content">Logout</span>
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
