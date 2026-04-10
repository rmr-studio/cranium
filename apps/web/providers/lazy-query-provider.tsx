"use client";

import dynamic from "next/dynamic";
import type { ReactNode } from "react";

// Code-split the react-query chunk without disabling SSR. Using `ssr: false`
// here previously caused every page to bail out to client-side rendering,
// which left Googlebot staring at an empty <body> and stalled indexing.
const QueryProvider = dynamic(() =>
  import("@/providers/query-provider").then((m) => m.QueryProvider),
);

export function LazyQueryProvider({ children }: { children: ReactNode }) {
  return <QueryProvider>{children}</QueryProvider>;
}
