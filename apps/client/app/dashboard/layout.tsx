import { AuthGuard } from '@/components/feature-modules/authentication/components/auth-guard';
import { OnboardWrapper } from '@/components/feature-modules/onboarding/context/onboard.wrapper';
import { BGPattern } from '@/components/ui/background/grids';
import { AppNavbar } from '@/components/ui/nav/app.navbar';
import { SidebarInset, SidebarProvider } from '@/components/ui/sidebar';
import { DashboardSidebar } from '@/components/ui/sidebar/dashboard-sidebar';
import type { ChildNodeProps } from '@riven/utils';
import { FC } from 'react';

const layout: FC<ChildNodeProps> = ({ children }) => {
  return (
    <AuthGuard>
      <OnboardWrapper>
        <SidebarProvider>
          <DashboardSidebar />
          <SidebarInset className="min-w-0 overflow-hidden bg-transparent">
            <BGPattern variant="grid" mask="fade-edges" className="opacity-5" />
            <header className="relative">
              <AppNavbar />
            </header>

            {children}
          </SidebarInset>
        </SidebarProvider>
      </OnboardWrapper>
    </AuthGuard>
  );
};

export default layout;
