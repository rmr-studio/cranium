'use client';

import type { ReactNode } from 'react';

export function DashboardContent({ children }: { children: ReactNode }) {
  return (
    <div className="min-w-0 flex-1 overflow-auto">
      {children}
    </div>
  );
}
