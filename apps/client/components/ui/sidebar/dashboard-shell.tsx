'use client';

import { DesktopShell } from '@/components/ui/sidebar/desktop-shell';
import { MobileShell } from '@/components/ui/sidebar/mobile-shell';
import { useIsMobile } from '@cranium/hooks';
import { type ReactNode } from 'react';

interface DashboardShellProps {
  children: ReactNode;
}

export function DashboardShell({ children }: DashboardShellProps) {
  const isMobile = useIsMobile();

  if (isMobile) {
    return <MobileShell>{children}</MobileShell>;
  }

  return <DesktopShell>{children}</DesktopShell>;
}
