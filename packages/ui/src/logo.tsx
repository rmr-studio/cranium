"use client";

import { cn } from "@riven/utils";

interface LogoProps extends React.SVGProps<SVGSVGElement> {
  /** Width of the logo in pixels (height auto-scales to maintain aspect ratio) */
  size?: number | string;
  /** Container rounded-rect background */
  backgroundClassName?: string;
  /** Left (tallest) pillar */
  primaryClassName?: string;
  /** Middle pillar */
  secondaryClassName?: string;
  /** Right (shortest) pillar */
  tertiaryClassName?: string;
}

const BG_PATH =
  "M1943.94 0H80C35.8172 0 0 35.8172 0 80V1882C0 1926.18 35.8173 1962 80.0001 1962H1943.94C1988.12 1962 2023.94 1926.18 2023.94 1882V80C2023.94 35.8172 1988.12 0 1943.94 0Z";

const PRIMARY_PATH =
  "M729.333 1506.77V341.555L505.113 341.548C441.761 341.555 390.413 392.909 390.413 456.248V1621.47L614.633 1621.47C677.991 1621.47 729.346 1570.13 729.333 1506.77Z";

const SECONDARY_PATH =
  "M1181.8 1050.34L1181.81 341.555H842.879V1165.05L1067.1 1165.04C1130.44 1165.05 1181.8 1113.69 1181.8 1050.34Z";

const TERTIARY_PATH =
  "M1634.27 692.491V384.428C1634.27 360.743 1615.08 341.548 1591.39 341.548L1295.35 341.555L1295.34 807.191H1519.57C1582.91 807.191 1634.26 755.843 1634.27 692.491Z";

export function LogoBackground({
  size = 32,
  className,
  backgroundClassName,
  primaryClassName,
  secondaryClassName,
  tertiaryClassName,
  ...props
}: LogoProps) {
  return (
    <svg
      viewBox="0 0 2024 1962"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      className={cn(className)}
      {...props}
    >
      <path
        d={BG_PATH}
        className={cn("fill-logo-background", backgroundClassName)}
      />
      <path
        d={PRIMARY_PATH}
        className={cn("fill-logo-primary", primaryClassName)}
      />
      <path
        d={SECONDARY_PATH}
        className={cn("fill-logo-secondary", secondaryClassName)}
      />
      <path
        d={TERTIARY_PATH}
        className={cn("fill-logo-tertiary", tertiaryClassName)}
      />
    </svg>
  );
}

export function Logo({
  size = 32,
  className,
  primaryClassName,
  secondaryClassName,
  tertiaryClassName,
  ...props
}: Omit<LogoProps, "backgroundClassName">) {
  return (
    <svg
      viewBox="390 341 1244 1281"
      fill="none"
      xmlns="http://www.w3.org/2000/svg"
      width={size}
      className={cn(className)}
      {...props}
    >
      <path
        d={PRIMARY_PATH}
        className={cn("fill-logo-primary", primaryClassName)}
      />
      <path
        d={SECONDARY_PATH}
        className={cn("fill-logo-secondary", secondaryClassName)}
      />
      <path
        d={TERTIARY_PATH}
        className={cn("fill-logo-tertiary", tertiaryClassName)}
      />
    </svg>
  );
}
