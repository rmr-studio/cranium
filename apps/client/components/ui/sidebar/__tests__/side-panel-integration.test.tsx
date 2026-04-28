import { render, screen, act } from '@testing-library/react';
import { SidePanelProvider, useSidePanelStore } from '@/components/ui/sidebar/context/side-panel-provider';
import type { SidePanelView } from '@/components/ui/sidebar/types/side-panel.types';

// Mock @riven/hooks
jest.mock('@riven/hooks', () => ({
  useIsMobile: () => false,
}));

/**
 * Test harness that renders children inside a SidePanelProvider.
 */
function TestHarness({ children }: { children: React.ReactNode }) {
  return <SidePanelProvider>{children}</SidePanelProvider>;
}

function StackDisplay() {
  const currentView = useSidePanelStore((s) => s.viewStack.at(-1));
  const stackDepth = useSidePanelStore((s) => s.viewStack.length);
  const pushView = useSidePanelStore((s) => s.pushView);
  const popView = useSidePanelStore((s) => s.popView);

  const testView: SidePanelView = {
    type: 'definition-detail',
    title: 'Test Definition',
    definitionId: 'def-123',
    workspaceId: 'ws-1',
  };

  return (
    <div>
      <span data-testid="depth">{stackDepth}</span>
      <span data-testid="current">{currentView?.title ?? 'none'}</span>
      <button onClick={() => pushView(testView)}>push</button>
      <button onClick={() => popView()}>pop</button>
    </div>
  );
}

describe('SidePanel integration', () => {
  beforeEach(() => {
    localStorage.clear();
  });

  it('push adds a view, pop removes it', () => {
    render(
      <TestHarness>
        <StackDisplay />
      </TestHarness>,
    );

    expect(screen.getByTestId('depth').textContent).toBe('0');
    expect(screen.getByTestId('current').textContent).toBe('none');

    act(() => {
      screen.getByText('push').click();
    });

    expect(screen.getByTestId('depth').textContent).toBe('1');
    expect(screen.getByTestId('current').textContent).toBe('Test Definition');

    act(() => {
      screen.getByText('pop').click();
    });

    expect(screen.getByTestId('depth').textContent).toBe('0');
    expect(screen.getByTestId('current').textContent).toBe('none');
  });
});
