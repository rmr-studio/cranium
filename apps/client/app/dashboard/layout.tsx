import { AuthGuard } from '@/components/feature-modules/authentication/components/auth-guard';
import { OnboardWrapper } from '@/components/feature-modules/onboarding/context/onboard.wrapper';
import { BGPattern } from '@/components/ui/background/grids';
import { AppNavbar } from '@/components/ui/nav/app.navbar';
import { DashboardContent } from '@/components/ui/sidebar/dashboard-content';
import { IconRailProvider } from '@/components/ui/sidebar/icon-rail-context';
import { IconRail } from '@/components/ui/sidebar/icon-rail';
import { SubPanel } from '@/components/ui/sidebar/sub-panel';
import type { ChildNodeProps } from '@riven/utils';
import { FC } from 'react';

const layout: FC<ChildNodeProps> = ({ children }) => {
  return (
    <AuthGuard>
      <OnboardWrapper>
        <IconRailProvider>
          <div className="flex h-screen w-full">
            <IconRail />
            <SubPanel />
            <DashboardContent>
              <BGPattern variant="grid" mask="fade-edges" className="opacity-5" />
              <header className="relative">
                <AppNavbar />
              </header>
              {children}
            </DashboardContent>
          </div>
        </IconRailProvider>
      </OnboardWrapper>
    </AuthGuard>
  );
};

export default layout;
