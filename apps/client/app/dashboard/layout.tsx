import { AuthGuard } from '@/components/feature-modules/authentication/components/auth-guard';
import { OnboardWrapper } from '@/components/feature-modules/onboarding/context/onboard.wrapper';
import { Navbar } from '@/components/ui/nav/navbar';
import { DashboardContent } from '@/components/ui/sidebar/dashboard-content';
import { IconRail } from '@/components/ui/sidebar/icon-rail';
import { IconRailProvider } from '@/components/ui/sidebar/icon-rail-context';
import { SubPanel } from '@/components/ui/sidebar/sub-panel';
import type { ChildNodeProps } from '@riven/utils';
import { FC } from 'react';

const layout: FC<ChildNodeProps> = ({ children }) => {
  return (
    <AuthGuard>
      <OnboardWrapper>
        <IconRailProvider>
          <div className="flex h-screen w-full bg-primary py-0.5 dark:bg-secondary">
            <IconRail />
            <SubPanel />
            <DashboardContent>
              <header className="relative">
                <Navbar />
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
