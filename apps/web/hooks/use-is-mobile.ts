import { Breakpoint, useBreakpoint } from './use-breakpoint';

export function useIsMobile(override: Breakpoint = 'sm'): boolean | undefined {
  const rank: Record<Breakpoint, number> = {
    sm: 1,
    md: 2,
    lg: 3,
    xl: 4,
  };

  const breakpoint = useBreakpoint();
  if (!breakpoint) return undefined;
  return rank[breakpoint] <= rank[override];
}
