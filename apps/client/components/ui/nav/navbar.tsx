'use client';

import { UserProfileDropdown } from '@/components/feature-modules/user/components/avatar-dropdown';
import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';
import { useIconRail } from '@/components/ui/sidebar/icon-rail-context';
import { Button } from '@riven/ui/button';
import { ThemeToggle } from '@riven/ui/theme-toggle';
import { Menu } from 'lucide-react';
import Link from 'next/dist/client/link';
import { FC } from 'react';
import { Skeleton } from '../skeleton';

export const Navbar = () => {
  const { setMobileOpen, isMobile } = useIconRail();

  return (
    <nav className="sticky top-0 flex h-[--header-height] w-auto flex-grow items-center border-b bg-background/40 px-4 backdrop-blur-[4px]">
      {isMobile && (
        <Button onClick={() => setMobileOpen(true)} variant="ghost" size="icon" className="mr-4">
          <Menu className="size-5" />
        </Button>
      )}
      <div className="mr-2 flex w-auto grow justify-end">
        <NavbarUserProfile />
      </div>
      <div className="flex items-center">
        <ThemeToggle />
      </div>
    </nav>
  );
};

export const NavbarUserProfile: FC = () => {
  const { isLoadingAuth, isLoading, data: user } = useProfile();
  if (isLoadingAuth || isLoading) return <Skeleton className="size-8 rounded-md" />;
  if (!user)
    return (
      <div className="flex">
        <Button variant={'outline'}>
          <Link href="/auth/login">Login</Link>
        </Button>
        <Button className="ml-2">
          <Link href="/auth/register">Get Started</Link>
        </Button>
      </div>
    );
  return <UserProfileDropdown user={user} />;
};
