'use client';

import { Button } from '@cranium/ui/button';
import { AlertCircle, RotateCcw } from 'lucide-react';

interface PanelErrorFallbackProps {
  onRetry: () => void;
}

export function PanelErrorFallback({ onRetry }: PanelErrorFallbackProps) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 py-12 text-center">
      <AlertCircle className="size-8 text-muted-foreground/40" />
      <p className="text-sm text-muted-foreground">Failed to load panel content</p>
      <Button variant="outline" size="sm" onClick={onRetry}>
        <RotateCcw className="mr-1.5 size-3.5" />
        Retry
      </Button>
    </div>
  );
}
