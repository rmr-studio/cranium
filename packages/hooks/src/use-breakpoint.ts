"use client";

import { useEffect, useState } from "react";

export type Breakpoint = "sm" | "md" | "lg" | "xl";

export function useBreakpoint(): Breakpoint | undefined {
  const [breakpoint, setBreakpoint] = useState<Breakpoint | undefined>(undefined);

  useEffect(() => {
    const mdQuery = window.matchMedia("(min-width: 768px)");
    const lgQuery = window.matchMedia("(min-width: 1024px)");
    const xlQuery = window.matchMedia("(min-width: 1280px)");

    const updateBreakpoint = () => {
      if (xlQuery.matches) {
        setBreakpoint("xl");
      } else if (lgQuery.matches) {
        setBreakpoint("lg");
      } else if (mdQuery.matches) {
        setBreakpoint("md");
      } else {
        setBreakpoint("sm");
      }
    };

    updateBreakpoint();

    mdQuery.addEventListener("change", updateBreakpoint);
    lgQuery.addEventListener("change", updateBreakpoint);
    xlQuery.addEventListener("change", updateBreakpoint);

    return () => {
      mdQuery.removeEventListener("change", updateBreakpoint);
      lgQuery.removeEventListener("change", updateBreakpoint);
      xlQuery.removeEventListener("change", updateBreakpoint);
    };
  }, []);

  return breakpoint;
}
