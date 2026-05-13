import { AuthGuard } from '@/components/feature-modules/authentication/components/auth-guard';
import { OnboardWrapper } from '@/components/feature-modules/onboarding/context/onboard-handler';
import { WebSocketSubscriptionManager } from '@/components/feature-modules/workspace/components/websocket-subscription-manager';
import { Navbar } from '@/components/ui/nav/navbar';
import { SidePanelProvider } from '@/components/ui/sidebar/context/side-panel-provider';
import { DashboardShell } from '@/components/ui/sidebar/dashboard-shell';
import type { ChildNodeProps } from '@cranium/utils';
import { FC } from 'react';

const layout: FC<ChildNodeProps> = ({ children }) => {
  return (
    <AuthGuard>
      <OnboardWrapper>
        <SidePanelProvider>
          <WebSocketSubscriptionManager />
          <DashboardShell>
            <header className="relative">
              <Navbar />
            </header>
            <section className="px-12 py-6">{children}</section>
          </DashboardShell>
        </SidePanelProvider>
      </OnboardWrapper>
    </AuthGuard>
  );
};

export default layout;
