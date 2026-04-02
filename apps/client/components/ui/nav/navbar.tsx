'use client';

import { UserProfileDropdown } from '@/components/feature-modules/user/components/avatar-dropdown';
import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { useIconRail } from '@/components/ui/sidebar/icon-rail-context';
import { Skeleton } from '@/components/ui/skeleton';
import { Button } from '@riven/ui/button';
import { ThemeToggle } from '@riven/ui/theme-toggle';
import { AnimatePresence, motion } from 'framer-motion';
import { Menu, PanelLeftOpen } from 'lucide-react';
import Link from 'next/link';
import { FC } from 'react';

export const Navbar = () => {
  const { setMobileOpen, isMobile, panelOpen, openPanel } = useIconRail();

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
          <motion.div
            initial={{ opacity: 0, width: 0 }}
            animate={{ opacity: 1, width: 'auto' }}
            exit={{ opacity: 0, width: 0 }}
            transition={{ duration: 0.15, delay: 0.1 }}
          >
            <Button onClick={openPanel} variant="ghost" size="icon" aria-label="Open sidebar">
              <PanelLeftOpen className="size-4" />
            </Button>
          </motion.div>
        )}
      </AnimatePresence>
      <div className="flex w-auto grow items-center justify-end space-x-2">
        <NavbarUserProfile />
        <ThemeToggle className="rounded-sm border" />
      </div>
    </nav>
  );
};

export const NavbarUserProfile: FC = () => {
  const { isLoadingAuth, isLoading, data: user } = useProfile();
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
  return <UserProfileDropdown user={user} />;
};
