'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';
import { useIconRail } from '@/components/ui/sidebar/icon-rail-context';
import { Menu } from 'lucide-react';
import { NavbarUserProfile, NavbarWrapper } from './navbar.content';

export const AppNavbar = () => {
  // eslint-disable-next-line @typescript-eslint/no-unused-vars
  const { isLoadingAuth: _, ...query } = useProfile();
  const { setMobileOpen, isMobile } = useIconRail();

  return (
    <NavbarWrapper>
      {isMobile && (
        <button
          onClick={() => setMobileOpen(true)}
          className="mr-4 flex size-8 cursor-pointer items-center justify-center rounded-md hover:bg-accent"
        >
          <Menu className="size-5" />
        </button>
      )}
      <div className="mr-2 flex w-auto flex-grow justify-end">
        <NavbarUserProfile {...query} />
      </div>
    </NavbarWrapper>
  );
};
