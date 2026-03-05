'use client';

import type { User } from '@/lib/types/user';
import { Avatar, AvatarFallback } from '@/components/ui/avatar';
import { DropdownMenu, DropdownMenuContent, DropdownMenuGroup, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from '@riven/ui/dropdown-menu';
import { isAuthError } from '@/lib/auth';
import { getAuthErrorMessage } from '@/lib/auth/error-messages';
import { getInitials } from '@riven/utils';

import {
  AppWindowMac,
  ArrowLeftToLine,
  Building2,
  ReceiptText,
  Settings,
  User as UserIcon,
} from 'lucide-react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FC } from 'react';
import { FaGithub } from 'react-icons/fa';
import { toast } from 'sonner';
import { useAuth } from '../../../provider/auth-context';

interface Props {
  user: User;
}

export const UserProfileDropdown: FC<Props> = ({ user }) => {
  const { name } = user;
  const { signOut } = useAuth();
  const router = useRouter();

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
        <Avatar className="cursor-pointer rounded-md">
          <AvatarFallback className="rounded-md">{getInitials(name)}</AvatarFallback>
        </Avatar>
      </DropdownMenuTrigger>
      <DropdownMenuContent className="mx-4 mt-1 px-2">
        <DropdownMenuGroup>
          <DropdownMenuItem className="pointer-events-none">
            <UserIcon />
            <span className="ml-2 text-xs font-semibold">{user.email}</span>
          </DropdownMenuItem>
          <DropdownMenuSeparator />
          <DropdownMenuItem>
            <AppWindowMac />
            <span className="text-content ml-2 text-xs" onClick={() => router.push('/dashboard')}>
              My Dashboard
            </span>
          </DropdownMenuItem>
          <DropdownMenuItem>
            <Building2 />
            <span
              className="text-content ml-2 text-xs"
              onClick={() => router.push('/dashboard/workspace')}
            >
              My Workspaces
            </span>
          </DropdownMenuItem>

          <DropdownMenuItem>
            <ReceiptText />
            <span
              className="text-content ml-2 text-xs"
              onClick={() => router.push('/dashboard/invoices')}
            >
              All Invoices
            </span>
          </DropdownMenuItem>
          <DropdownMenuItem>
            <Settings />
            <span
              className="text-content ml-2 text-xs"
              onClick={() => router.push('/dashboard/settings')}
            >
              Account Preferences
            </span>
          </DropdownMenuItem>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <Link href={'https://www.github.com/usepaladin'} target="_blank">
            <DropdownMenuItem>
              <FaGithub />

              <span className="text-content ml-2 text-xs">Source Code</span>
            </DropdownMenuItem>
          </Link>
        </DropdownMenuGroup>
        <DropdownMenuSeparator />
        <DropdownMenuGroup>
          <DropdownMenuItem>
            <ArrowLeftToLine />
            <span className="text-content ml-2 text-xs" onClick={async () => handleLogout()}>
              Logout
            </span>
          </DropdownMenuItem>
        </DropdownMenuGroup>
      </DropdownMenuContent>
    </DropdownMenu>
  );
};
