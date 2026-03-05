'use client';

import React from 'react';
import { ImagePlus } from 'lucide-react';

import { cn } from '@riven/utils';

import { Button } from '../button';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from '../dialog';
import { INSERT_COMPONENTS, type InsertComponent } from './insert-components-data';

interface InsertComponentsModalProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSelect: (componentId: string) => void;
}

// Icon mapping for components
const componentIcons: Record<string, React.ComponentType<{ className?: string }>> = {
  'free-image': ImagePlus,
};

export function InsertComponentsModal({
  open,
  onOpenChange,
  onSelect,
}: InsertComponentsModalProps) {
  const handleSelect = (componentId: string) => {
    onSelect(componentId);
    onOpenChange(false);
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-w-3xl">
        <DialogHeader className="space-y-3">
          <DialogTitle className="flex items-center gap-3 text-2xl">
            <div className="rounded-lg bg-primary/10 p-2">
              <ImagePlus className="h-6 w-6 text-primary" />
            </div>
            Insert Component
          </DialogTitle>
          <DialogDescription className="text-base">
            Choose a component to insert into your document
          </DialogDescription>
        </DialogHeader>

        {/* Components Grid */}
        <div className="mt-6 grid grid-cols-1 gap-4 md:grid-cols-2">
          {INSERT_COMPONENTS.map((component) => {
            const Icon = componentIcons[component.id] || ImagePlus;

            return (
              <button
                key={component.id}
                onClick={() => handleSelect(component.id)}
                className={cn(
                  'group relative rounded-xl border border-border/60 p-6',
                  'hover:border-primary/60 hover:shadow-lg hover:shadow-primary/10',
                  'hover:scale-[1.02] hover:bg-accent/30',
                  'transition-all duration-300 ease-out',
                  'bg-background/50 backdrop-blur-sm',
                  'text-left',
                )}
              >
                {/* Icon */}
                <div className="mb-3 flex items-center gap-4">
                  <div className="rounded-lg bg-primary/10 p-3 transition-colors group-hover:bg-primary/20">
                    <Icon className="h-6 w-6 text-primary" />
                  </div>
                  <div className="text-3xl">{component.icon}</div>
                </div>

                {/* Component Info */}
                <h3 className="mb-2 text-lg font-semibold transition-colors duration-200 group-hover:text-primary">
                  {component.name}
                </h3>
                <p className="text-sm leading-relaxed text-muted-foreground">
                  {component.description}
                </p>

                {/* Category badge */}
                <div className="mt-4">
                  <span className="inline-flex items-center rounded-full bg-muted/80 px-2.5 py-0.5 text-xs font-medium text-muted-foreground">
                    {component.category}
                  </span>
                </div>
              </button>
            );
          })}
        </div>

        {/* Empty state for future */}
        {INSERT_COMPONENTS.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 text-muted-foreground">
            <ImagePlus className="mb-4 h-12 w-12 opacity-50" />
            <p className="text-base">No components available</p>
          </div>
        )}
      </DialogContent>
    </Dialog>
  );
}
