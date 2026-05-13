"use client";

import { useEffect, useRef } from "react";
import { AnimatePresence, motion } from "framer-motion";
import { PanelLeft } from "lucide-react";
import { Button } from '@cranium/ui/button';
import { WorkflowNodeMetadata } from "@/lib/types/workflow";
import {
    useSelectedNodeId,
    useSidebarCollapsed,
    useSetSidebarCollapsed,
} from "../../context/workflow-canvas-provider";
import { NodeConfigDrawer } from "../config/node-config-drawer";
import { NodeLibrarySidebar } from "./node-library-sidebar";

interface WorkflowSidebarProps {
    /** Workspace ID for entity widgets */
    workspaceId: string;
    /** Callback when a node is clicked to add to canvas (receives backend key and metadata) */
    onClickAdd?: (nodeTypeKey: string, metadata: WorkflowNodeMetadata) => void;
}

const SIDEBAR_WIDTH = 384; // 24rem = 384px (w-96)
const COLLAPSED_WIDTH = 16; // Just enough for the button anchor

/**
 * Manages which sidebar to display based on workflow state
 *
 * Shows NodeConfigDrawer when a node is selected, otherwise shows NodeLibrarySidebar.
 * Includes smooth animations for transitions between sidebars and collapse/expand.
 * Supports collapse/expand with state managed in the workflow canvas store.
 */
export function WorkflowSidebar({ workspaceId, onClickAdd }: WorkflowSidebarProps) {
    const selectedNodeId = useSelectedNodeId();
    const isCollapsed = useSidebarCollapsed();
    const setCollapsed = useSetSidebarCollapsed();
    const showConfigDrawer = selectedNodeId !== null;

    // Track the last selected node ID so we can render the drawer during exit animation
    const lastNodeIdRef = useRef<string | null>(selectedNodeId);

    useEffect(() => {
        if (selectedNodeId !== null) {
            lastNodeIdRef.current = selectedNodeId;
        }
    }, [selectedNodeId]);

    // Use the current node ID if available, otherwise use the last one for exit animation
    const nodeIdForDrawer = selectedNodeId ?? lastNodeIdRef.current;

    return (
        <motion.div
            className="relative h-full border-l bg-background"
            initial={false}
            animate={{
                width: isCollapsed ? COLLAPSED_WIDTH : SIDEBAR_WIDTH,
            }}
            transition={{
                duration: 0.3,
                ease: [0.4, 0, 0.2, 1], // ease-out cubic
            }}
            style={{ overflow: "visible" }} // Allow button to overflow
        >
            {/* Collapse/Expand toggle button - positioned to overflow left */}
            <Button
                variant="ghost"
                size="icon"
                onClick={() => setCollapsed(!isCollapsed)}
                className="absolute top-3 -left-4 z-20 h-8 w-8 rounded-full border bg-background shadow-sm hover:bg-accent"
                aria-label={isCollapsed ? "Expand sidebar" : "Collapse sidebar"}
            >
                <motion.div
                    initial={false}
                    animate={{ rotate: isCollapsed ? 180 : 0 }}
                    transition={{ duration: 0.3, ease: [0.4, 0, 0.2, 1] }}
                >
                    <PanelLeft className="size-3.5 text-primary/90" />
                </motion.div>
            </Button>

            {/* Sidebar content container */}
            <motion.div
                className="h-full w-full"
                initial={false}
                animate={{
                    opacity: isCollapsed ? 0 : 1,
                }}
                transition={{
                    duration: 0.2,
                    ease: "easeOut",
                    // Delay opacity fade-in when expanding
                    delay: isCollapsed ? 0 : 0.1,
                }}
                style={{
                    pointerEvents: isCollapsed ? "none" : "auto",
                    overflow: "hidden",
                }}
            >
                <AnimatePresence mode="wait" initial={false}>
                    {showConfigDrawer ? (
                        <motion.div
                            key="config-drawer"
                            initial={{ opacity: 0, x: 20 }}
                            animate={{ opacity: 1, x: 0 }}
                            exit={{ opacity: 0, x: 20 }}
                            transition={{ duration: 0.2, ease: "easeOut" }}
                            className="h-full"
                            style={{ width: SIDEBAR_WIDTH }}
                        >
                            <NodeConfigDrawer
                                workspaceId={workspaceId}
                                nodeId={nodeIdForDrawer}
                            />
                        </motion.div>
                    ) : (
                        <motion.div
                            key="node-library"
                            initial={{ opacity: 0, x: -20 }}
                            animate={{ opacity: 1, x: 0 }}
                            exit={{ opacity: 0, x: -20 }}
                            transition={{ duration: 0.2, ease: "easeOut" }}
                            className="h-full"
                            style={{ width: SIDEBAR_WIDTH }}
                        >
                            <NodeLibrarySidebar onClickAdd={onClickAdd} className="h-full border-l-0" />
                        </motion.div>
                    )}
                </AnimatePresence>
            </motion.div>
        </motion.div>
    );
}
