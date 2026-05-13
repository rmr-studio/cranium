'use client';

import { Button } from '@cranium/ui/button';
import { ChevronLeft, X } from 'lucide-react';
import { useEffect, useRef } from 'react';

interface PanelViewFrameProps {
  title: string;
  onBack?: () => void;
  onClose?: () => void;
  children: React.ReactNode;
}

export function PanelViewFrame({ title, onBack, onClose, children }: PanelViewFrameProps) {
  const titleRef = useRef<HTMLSpanElement>(null);

  // Focus management: move focus to title when view mounts
  useEffect(() => {
    titleRef.current?.focus();
  }, [title]);

  return (
    <div className="flex h-full flex-col">
      {/* Header — px-4 matches existing sub-panel header (per DESIGN.md alignment) */}
      <div className="flex min-h-(--header-height) shrink-0 items-center gap-2 border-b px-4">
        {onBack && (
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0"
            onClick={onBack}
            aria-label="Back"
          >
            <ChevronLeft className="size-4" />
          </Button>
        )}
        <span
          ref={titleRef}
          tabIndex={-1}
          className="min-w-0 flex-1 truncate text-sm font-medium text-sidebar-foreground outline-none"
        >
          {title}
        </span>
        {onClose && (
          <Button
            variant="ghost"
            size="icon"
            className="size-7 shrink-0 text-sidebar-foreground/70 hover:text-sidebar-foreground"
            onClick={onClose}
            aria-label="Close"
          >
            <X className="size-4" />
          </Button>
        )}
      </div>

      {/* Scrollable content */}
      <div className="flex-1 overflow-y-auto px-3 py-2">{children}</div>
    </div>
  );
}
