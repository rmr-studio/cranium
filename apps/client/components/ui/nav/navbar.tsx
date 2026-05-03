'use client';

import { UserProfileDropdown } from '@/components/feature-modules/user/components/avatar-dropdown';
import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { ConnectionStatus } from '@/components/feature-modules/workspace/components/connection-status';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import {
  usePanelOpen,
  useSidePanelActions,
} from '@/components/ui/sidebar/context/side-panel-provider';
import { Skeleton } from '@/components/ui/skeleton';
import { useIsMobile } from '@riven/hooks';
import { Button } from '@riven/ui/button';
import { ThemeToggle } from '@riven/ui/theme-toggle';
import { AnimatePresence } from 'framer-motion';
import { Menu, PanelLeftOpen } from 'lucide-react';
import Link from 'next/link';
import { FC } from 'react';

export const Navbar = () => {
  const { setMobileOpen, openPanel } = useSidePanelActions();
  const panelOpen = usePanelOpen();
  const isMobile = useIsMobile();

  const showReopenButton = !isMobile && !panelOpen;

  return (
    <nav className="sticky top-0 flex h-(--header-height) w-auto flex-grow items-center border-b bg-background/40 px-4">
      {isMobile && (
        <Button
          onClick={() => setMobileOpen(true)}
          variant="ghost"
          size="icon"
          className="mr-4"
          aria-label="Open menu"
        >
          <Menu className="size-5" />
        </Button>
      )}
      <AnimatePresence>
        {showReopenButton && (
          <Button onClick={openPanel} variant="ghost" size="icon" aria-label="Open sidebar">
            <PanelLeftOpen className="size-4" />
          </Button>
        )}
      </AnimatePresence>
      <div className="flex w-auto grow items-center justify-end space-x-2">
        <ConnectionStatus />

        <ThemeToggle />
        <NavbarUserProfile />
      </div>
    </nav>
  );
};

export const NavbarUserProfile: FC = () => {
  const { isLoadingAuth, isLoading, data: user } = useProfile();
  const selectedWorkspaceId = useWorkspaceStore((s) => s.selectedWorkspaceId);
  if (isLoadingAuth || isLoading) return <Skeleton className="size-9 rounded-sm" />;
  if (!user)
    return (
      <div className="flex">
        <Button variant={'outline'} asChild>
          <Link href="/auth/login">Login</Link>
        </Button>
        <Button className="ml-2" asChild>
          <Link href="/auth/register">Get Started</Link>
        </Button>
      </div>
    );
  return <UserProfileDropdown user={user} workspaceId={selectedWorkspaceId} />;
};
