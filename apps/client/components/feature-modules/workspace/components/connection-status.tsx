'use client';

import { useWebSocket } from '@/hooks/websocket/use-websocket';
import type { ConnectionState } from '@/components/provider/websocket-provider';
import { Tooltip, TooltipContent, TooltipTrigger } from '@/components/ui/tooltip';
import { cn } from '@cranium/utils';

const stateConfig: Record<ConnectionState, { color: string; pulse: boolean; label: string }> = {
  CONNECTED: { color: 'bg-green-500', pulse: false, label: 'Connected' },
  CONNECTING: { color: 'bg-yellow-500', pulse: true, label: 'Connecting...' },
  RECONNECTING: { color: 'bg-yellow-500', pulse: true, label: 'Reconnecting...' },
  DISCONNECTED: { color: 'bg-red-500', pulse: false, label: 'Disconnected' },
  AUTH_FAILED: { color: 'bg-red-500', pulse: false, label: 'Authentication failed' },
};

export function ConnectionStatus() {
  const { connectionState, lastConnectedAt } = useWebSocket();

  // Don't render anything if WS is not initialized (no session yet)
  if (connectionState === 'DISCONNECTED' && !lastConnectedAt) return null;

  const config = stateConfig[connectionState];
  const lastConnectedLabel = lastConnectedAt
    ? `Last connected: ${lastConnectedAt.toLocaleTimeString()}`
    : 'Never connected';

  return (
    <Tooltip>
      <TooltipTrigger asChild>
        <span className="relative flex h-2.5 w-2.5 cursor-default">
          {config.pulse && (
            <span
              className={cn('absolute inline-flex h-full w-full animate-ping rounded-full opacity-75', config.color)}
            />
          )}
          <span className={cn('relative inline-flex h-2.5 w-2.5 rounded-full', config.color)} />
        </span>
      </TooltipTrigger>
      <TooltipContent side="bottom">
        <p>{config.label}</p>
        <p className="text-muted-foreground text-xs">{lastConnectedLabel}</p>
      </TooltipContent>
    </Tooltip>
  );
}
