'use client';

import { useCallback, useEffect, type FC } from 'react';
import { ArrowLeft } from 'lucide-react';
import { Button } from '@riven/ui/button';
import { ScrollArea } from '@riven/ui/scroll-area';
import { Separator } from '@riven/ui/separator';
import { IconCell } from '@/components/ui/icon/icon-cell';
import { IconColour } from '@/lib/types/common';
import {
  useWorkflowNodes,
  useSelectNode,
  useUpdateNodeData,
  useNodeConfig,
  useSchemasLoading,
} from '../../context/workflow-canvas-provider';
import { NodeConfigForm } from './node-config-form';

interface NodeConfigDrawerProps {
  /** Workspace ID for entity widgets */
  workspaceId: string;
  /** Optional node ID override - used during exit animation to render stale data */
  nodeId?: string | null;
}

/**
 * Right-side drawer displaying configuration form for selected node
 *
 * Features:
 * - Shows node icon, label, and description in header
 * - Loads config schema from backend based on node type
 * - Renders dynamic form with immediate value sync
 * - Close button to deselect node
 * - Escape key to close drawer (CONFIG-08)
 */
export const NodeConfigDrawer: FC<NodeConfigDrawerProps> = ({ workspaceId, nodeId }) => {
  const nodes = useWorkflowNodes();
  const selectNode = useSelectNode();
  const updateNodeData = useUpdateNodeData();
  const nodeConfig = useNodeConfig();
  const schemasLoading = useSchemasLoading();

  // Find the node by the provided nodeId prop (supports exit animation with stale ID)
  const node = nodeId ? nodes.find((n) => n.id === nodeId) : undefined;

  const handleClose = useCallback(() => {
    selectNode(null);
  }, [selectNode]);

  // Handle Escape key to close drawer (CONFIG-08)
  useEffect(() => {
    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.key === 'Escape' && node) {
        event.preventDefault();
        handleClose();
      }
    };

    document.addEventListener('keydown', handleKeyDown);
    return () => document.removeEventListener('keydown', handleKeyDown);
  }, [node, handleClose]);

  const handleValuesChange = useCallback(
    (values: Record<string, unknown>) => {
      if (!node) return;

      // Update node data with new config values
      // Set configured to true if any values are set
      const hasConfig = Object.values(values).some(
        (v) => v !== undefined && v !== null && v !== '',
      );
      updateNodeData(node.id, {
        config: values,
        configured: hasConfig,
      });
    },
    [node, updateNodeData],
  );

  if (!node) return null;

  // Use nodeTypeKey from node data to look up schema
  const nodeMetadata = nodeConfig?.[node.data.nodeTypeKey];
  const configSchema = nodeMetadata?.schema ?? [];

  return (
    <div className="flex h-full flex-col bg-background">
      {/* Header */}
      <div className="flex items-center justify-between border-b p-4">
        <div className="flex items-center gap-3">
          <div className="rounded-md bg-muted p-2">
            <IconCell readonly type={node.data.icon} colour={IconColour.Neutral} />
          </div>
          <div>
            <h3 className="text-sm font-semibold">{node.data.label}</h3>
            <p className="text-xs text-muted-foreground">{node.data.description}</p>
          </div>
        </div>
        <Button
          variant="ghost"
          size="icon"
          onClick={handleClose}
          className="h-8 w-8"
          aria-label="Close drawer (Escape)"
        >
          <ArrowLeft className="h-4 w-4" />
          <span className="sr-only">Close</span>
        </Button>
      </div>

      <Separator />

      {/* Form content */}
      <ScrollArea className="flex-1">
        <div className="p-4">
          {schemasLoading ? (
            <div className="flex items-center justify-center py-8">
              <div className="text-sm text-muted-foreground">Loading configuration...</div>
            </div>
          ) : (
            <NodeConfigForm
              nodeId={node.id}
              nodeType={node.type}
              configSchema={configSchema}
              initialValues={node.data.config}
              onValuesChange={handleValuesChange}
              workspaceId={workspaceId}
            />
          )}
        </div>
      </ScrollArea>
    </div>
  );
};
