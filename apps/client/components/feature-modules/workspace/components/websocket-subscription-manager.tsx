'use client';

import { useWorkspaceSubscription } from '@/hooks/websocket/use-workspace-subscription';
import { useCurrentWorkspace } from '@/components/feature-modules/workspace/provider/workspace-provider';

/**
 * Headless component that manages WebSocket subscriptions for the active workspace.
 * Placed in the dashboard layout to ensure subscriptions are active whenever the user is authenticated.
 */
export function WebSocketSubscriptionManager() {
  const { selectedWorkspaceId } = useCurrentWorkspace();
  useWorkspaceSubscription(selectedWorkspaceId);
  return null;
}
