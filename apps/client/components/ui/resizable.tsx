'use client';

import * as React from 'react';
import { GripVerticalIcon } from 'lucide-react';
import * as ResizablePrimitive from 'react-resizable-panels';

import { cn } from '@cranium/utils';

/**
 * Wrapper around `react-resizable-panels`'s `PanelGroup` that applies default layout classes and a `data-slot`.
 *
 * Renders a `PanelGroup` with base flex/grid classes (including support for vertical direction via `data-[panel-group-direction=vertical]`) and merges any provided `className` with those defaults. All other props are forwarded to the underlying `PanelGroup`.
 *
 * @param className - Additional CSS classes to merge with the component's default classes.
 * @returns A `PanelGroup` element with composed classes and forwarded props.
 */
function ResizablePanelGroup({
  className,
  ...props
}: React.ComponentProps<typeof ResizablePrimitive.PanelGroup>) {
  return (
    <ResizablePrimitive.PanelGroup
      data-slot="resizable-panel-group"
      className={cn('flex h-full w-full data-[panel-group-direction=vertical]:flex-col', className)}
      {...props}
    />
  );
}

/**
 * Wrapper around react-resizable-panels' Panel that forwards all props and sets `data-slot="resizable-panel"`.
 *
 * Passes received props through to the underlying Panel primitive; useful for applying a consistent
 * slot attribute and any shared styling or behavior at the call site.
 *
 * @returns A configured Panel element.
 */
function ResizablePanel({ ...props }: React.ComponentProps<typeof ResizablePrimitive.Panel>) {
  return <ResizablePrimitive.Panel data-slot="resizable-panel" {...props} />;
}

/**
 * A styled wrapper for react-resizable-panels' PanelResizeHandle that optionally renders a visible grip.
 *
 * Renders a PanelResizeHandle with opinionated default classes (handles both horizontal and vertical group directions)
 * and a data-slot="resizable-handle" attribute. Forwards all other props to the underlying PanelResizeHandle.
 *
 * @param withHandle - When true, renders an inner grip element (with a vertical grip icon) to provide a visible drag affordance.
 * @param className - Additional CSS class names to merge with the component's default styling.
 */
function ResizableHandle({
  withHandle,
  className,
  ...props
}: React.ComponentProps<typeof ResizablePrimitive.PanelResizeHandle> & {
  withHandle?: boolean;
}) {
  return (
    <ResizablePrimitive.PanelResizeHandle
      data-slot="resizable-handle"
      className={cn(
        'relative flex w-px items-center justify-center bg-border after:absolute after:inset-y-0 after:left-1/2 after:w-1 after:-translate-x-1/2 focus-visible:ring-1 focus-visible:ring-ring focus-visible:ring-offset-1 focus-visible:outline-hidden data-[panel-group-direction=vertical]:h-px data-[panel-group-direction=vertical]:w-full data-[panel-group-direction=vertical]:after:left-0 data-[panel-group-direction=vertical]:after:h-1 data-[panel-group-direction=vertical]:after:w-full data-[panel-group-direction=vertical]:after:translate-x-0 data-[panel-group-direction=vertical]:after:-translate-y-1/2 [&[data-panel-group-direction=vertical]>div]:rotate-90',
        className,
      )}
      {...props}
    >
      {withHandle && (
        <div className="z-10 flex h-4 w-3 items-center justify-center rounded-xs border bg-border">
          <GripVerticalIcon className="size-2.5" />
        </div>
      )}
    </ResizablePrimitive.PanelResizeHandle>
  );
}

export { ResizablePanelGroup, ResizablePanel, ResizableHandle };
