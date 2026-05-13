"use client";

import { useState } from "react";
import { ChevronDown } from "lucide-react";
import type { LucideIcon } from "lucide-react";
import { cn } from '@cranium/utils';
import {
    Collapsible,
    CollapsibleContent,
    CollapsibleTrigger,
} from "@/components/ui/collapsible";
import { WorkflowNodeType, WorkflowNodeMetadata } from "@/lib/types/workflow";
import type { NodeLibraryItem as NodeLibraryItemType } from "../../hooks/use-node-library";
import { NodeLibraryItem } from "./node-library-item";

export interface NodeCategorySectionProps {
    /** Category enum value (TRIGGER, ACTION, CONTROL_FLOW, etc.) */
    category: WorkflowNodeType;
    /** Display label for the category */
    label: string;
    /** Icon component for the category */
    icon: LucideIcon;
    /** Node library items in this category */
    items: NodeLibraryItemType[];
    /** Callback when a node is clicked to add to canvas */
    onClickAdd?: (nodeTypeKey: string, metadata: WorkflowNodeMetadata) => void;
}

/**
 * Collapsible section for a category of nodes in the library sidebar
 * Shows category icon, label, count badge, and expandable list of nodes
 */
export function NodeCategorySection({
    category,
    label,
    icon: Icon,
    items,
    onClickAdd,
}: NodeCategorySectionProps) {
    const [isOpen, setIsOpen] = useState(true);

    if (items.length === 0) {
        return null;
    }

    return (
        <Collapsible open={isOpen} onOpenChange={setIsOpen}>
            <CollapsibleTrigger className="flex w-full items-center justify-between rounded-md px-2 py-1.5 text-sm font-medium hover:bg-accent">
                <div className="flex items-center gap-2">
                    <Icon className="h-4 w-4 text-muted-foreground" />
                    <span>{label}</span>
                    <span className="rounded-full bg-muted px-2 py-0.5 text-xs text-muted-foreground">
                        {items.length}
                    </span>
                </div>
                <ChevronDown
                    className={cn(
                        "h-4 w-4 text-muted-foreground transition-transform duration-200",
                        isOpen && "rotate-180"
                    )}
                />
            </CollapsibleTrigger>
            <CollapsibleContent>
                <div className="flex flex-col gap-2 py-2">
                    {items.map((item) => (
                        <NodeLibraryItem
                            key={item.key}
                            nodeTypeKey={item.key}
                            metadata={item.metadata}
                            onClickAdd={onClickAdd}
                        />
                    ))}
                </div>
            </CollapsibleContent>
        </Collapsible>
    );
}
