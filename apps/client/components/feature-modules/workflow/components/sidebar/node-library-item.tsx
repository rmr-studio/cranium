"use client";

import type { DragEvent } from "react";
import { cn } from '@riven/utils';
import { WorkflowNodeType, WorkflowNodeMetadata } from "@/lib/types/workflow";
import { IconColour } from "@/lib/types/common";
import { IconCell } from "@/components/ui/icon/icon-cell";

export interface NodeLibraryItemProps {
    /** Full backend key (e.g., TRIGGER.ENTITY_EVENT) */
    nodeTypeKey: string;
    /** Node metadata from backend */
    metadata: WorkflowNodeMetadata;
    /** Callback when item is clicked to add to canvas */
    onClickAdd?: (nodeTypeKey: string, metadata: WorkflowNodeMetadata) => void;
}

/** Color classes for each node category */
const categoryColors: Record<WorkflowNodeType, string> = {
    [WorkflowNodeType.Trigger]: "border-l-amber-500",
    [WorkflowNodeType.Action]: "border-l-blue-500",
    [WorkflowNodeType.ControlFlow]: "border-l-purple-500",
    [WorkflowNodeType.Function]: "border-l-green-500",
    [WorkflowNodeType.Utility]: "border-l-cyan-500",
    [WorkflowNodeType.Parse]: "border-l-orange-500",
};

/** Background hover colors for each category */
const categoryHoverColors: Record<WorkflowNodeType, string> = {
    [WorkflowNodeType.Trigger]: "hover:bg-amber-500/10",
    [WorkflowNodeType.Action]: "hover:bg-blue-500/10",
    [WorkflowNodeType.ControlFlow]: "hover:bg-purple-500/10",
    [WorkflowNodeType.Function]: "hover:bg-green-500/10",
    [WorkflowNodeType.Utility]: "hover:bg-cyan-500/10",
    [WorkflowNodeType.Parse]: "hover:bg-orange-500/10",
};

/** Icon colours for each node category */
const categoryIconColours: Record<WorkflowNodeType, IconColour> = {
    [WorkflowNodeType.Trigger]: IconColour.Orange,
    [WorkflowNodeType.Action]: IconColour.Blue,
    [WorkflowNodeType.ControlFlow]: IconColour.Purple,
    [WorkflowNodeType.Function]: IconColour.Green,
    [WorkflowNodeType.Utility]: IconColour.Teal,
    [WorkflowNodeType.Parse]: IconColour.Orange,
};

/**
 * Draggable node item for the library sidebar
 * Displays node icon, label, and description with category-colored accent
 */
export function NodeLibraryItem({ nodeTypeKey, metadata, onClickAdd }: NodeLibraryItemProps) {
    const { label, description, icon, category } = metadata.metadata;

    const handleDragStart = (event: DragEvent<HTMLDivElement>) => {
        // Store both key and metadata in transfer data
        event.dataTransfer.setData("application/reactflow", nodeTypeKey);
        event.dataTransfer.setData("application/reactflow-metadata", JSON.stringify(metadata));
        event.dataTransfer.effectAllowed = "move";
    };

    const handleClick = () => {
        onClickAdd?.(nodeTypeKey, metadata);
    };

    return (
        <div
            draggable
            onDragStart={handleDragStart}
            onClick={handleClick}
            className={cn(
                "flex items-start gap-3 rounded-md border border-l-4 bg-card p-3",
                "cursor-grab transition-colors",
                "active:cursor-grabbing",
                categoryColors[category],
                categoryHoverColors[category]
            )}
        >
            <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-md bg-muted">
                <IconCell readonly type={icon} colour={categoryIconColours[category]} />
            </div>
            <div className="flex flex-col gap-0.5 overflow-hidden">
                <span className="truncate text-sm font-medium">{label}</span>
                <span className="line-clamp-2 text-xs text-muted-foreground">
                    {description}
                </span>
            </div>
        </div>
    );
}
