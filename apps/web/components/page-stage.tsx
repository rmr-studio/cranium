import React from 'react';

export function PageStage({ children }: { children: React.ReactNode }) {
  return (
    <>
      <div className="relative mx-auto w-full lg:max-w-[min(100dvw,var(--breakpoint-3xl))]">
        {children}
      </div>
    </>
  );
}
