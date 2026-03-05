'use client';

import { Button } from '@riven/ui/button';
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Sheet, SheetContent, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { cn } from '@riven/utils';
import { AlertCircle, ChevronRight } from "lucide-react";
import { FC, useEffect, useState } from "react";
import { useBlockEdit } from "../../context/block-edit-provider";
import { useBlockEnvironment } from "../../context/block-environment-provider";
import { isContentNode } from "@/lib/types/block";
import { BlockForm } from "./block-form";

export const BlockEditDrawer: FC = () => {
  const {
    drawerState,
    closeDrawer,
    validateBlock,
    startEdit,
    drawerState: { expandedSections },
    toggleSection,
  } = useBlockEdit();
  const { getBlock, getChildren } = useBlockEnvironment();
  const [isSaving, setIsSaving] = useState(false);
  const [validationErrors, setValidationErrors] = useState<Map<string, boolean>>(new Map());

  const getAllDescendantBlocks = (blockId: string): string[] => {
    const result = [blockId];
    const children = getChildren(blockId);

    children.forEach((childId) => {
      result.push(...getAllDescendantBlocks(childId));
    });

    return result;
  };

  // Start edit sessions for all descendants when drawer opens
  useEffect(() => {
    if (!drawerState.isOpen || !drawerState.rootBlockId) return;

    const allBlocks = getAllDescendantBlocks(drawerState.rootBlockId);

    // Start drawer edit sessions for ALL blocks with force refresh
    // This ensures all blocks start with fresh draft data from the environment
    allBlocks.forEach((blockId) => {
      startEdit(blockId, 'drawer', true); // forceRefresh=true
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [drawerState.isOpen, drawerState.rootBlockId]);

  if (!drawerState.isOpen || !drawerState.rootBlockId) return null;

  const rootBlock = getBlock(drawerState.rootBlockId);

  const handleSaveAll = async () => {
    setIsSaving(true);
    setValidationErrors(new Map());

    if (!drawerState.rootBlockId) {
      setIsSaving(false);
      return;
    }

    // Validate all blocks in the drawer
    const blocksToValidate = getAllDescendantBlocks(drawerState.rootBlockId);
    const errors = new Map<string, boolean>();

    blocksToValidate.forEach((blockId) => {
      const isValid = validateBlock(blockId);
      if (!isValid) {
        errors.set(blockId, true);
      }
    });

    if (errors.size > 0) {
      setValidationErrors(errors);
      setIsSaving(false);
      return;
    }

    // Save all blocks
    await closeDrawer(true);
    setIsSaving(false);
  };

  const handleCancel = () => {
    closeDrawer(false);
  };

  return (
    <Sheet open={drawerState.isOpen} onOpenChange={(open) => !open && handleCancel()}>
      <SheetContent side="right" className="w-[600px] overflow-y-auto sm:max-w-[600px]">
        <SheetHeader className="space-y-2">
          <SheetTitle>
            Edit {rootBlock?.block.name || rootBlock?.block.type.name || 'Block'}
          </SheetTitle>
          {validationErrors.size > 0 && (
            <div className="flex items-center gap-2 rounded bg-destructive/10 p-2 text-sm text-destructive">
              <AlertCircle className="h-4 w-4" />
              <span>
                Validation failed for {validationErrors.size} block
                {validationErrors.size !== 1 ? 's' : ''}. Please fix errors before saving.
              </span>
            </div>
          )}
        </SheetHeader>

        <div className="space-y-6 py-6">
          {/* Root block form if it has form data */}
          {rootBlock && isContentNode(rootBlock) && hasFormFields(rootBlock) && (
            <div className="space-y-2">
              <h3 className="text-sm font-semibold text-muted-foreground uppercase">Container</h3>
              <div
                className={cn(
                  'rounded-lg border p-4',
                  validationErrors.has(drawerState.rootBlockId) && 'border-destructive',
                )}
              >
                <BlockForm
                  blockId={drawerState.rootBlockId}
                  blockType={rootBlock.block.type}
                  mode="drawer"
                />
              </div>
            </div>
          )}

          {/* Recursive child forms */}
          <div className="space-y-2">
            <h3 className="text-sm font-semibold text-muted-foreground uppercase">Children</h3>
            <RecursiveFormRenderer
              blockId={drawerState.rootBlockId}
              depth={0}
              validationErrors={validationErrors}
            />
          </div>
        </div>

        <SheetFooter className="flex gap-2">
          <Button variant="outline" onClick={handleCancel} disabled={isSaving}>
            Cancel
          </Button>
          <Button onClick={handleSaveAll} disabled={isSaving}>
            {isSaving ? 'Saving...' : 'Save All Changes'}
          </Button>
        </SheetFooter>
      </SheetContent>
    </Sheet>
  );
};

/* -------------------------------------------------------------------------- */
/*                          Recursive Form Renderer                           */
/* -------------------------------------------------------------------------- */

interface RecursiveFormRendererProps {
  blockId: string;
  depth: number;
  validationErrors: Map<string, boolean>;
}

const RecursiveFormRenderer: FC<RecursiveFormRendererProps> = ({
  blockId,
  depth,
  validationErrors,
}) => {
  const { getChildren, getBlock } = useBlockEnvironment();
  const { drawerState, toggleSection } = useBlockEdit();
  const children = getChildren(blockId);

  if (children.length === 0) {
    return (
      <div className="rounded border border-dashed p-4 text-sm text-muted-foreground italic">
        No children
      </div>
    );
  }

  return (
    <div className={cn('space-y-3', depth > 0 && 'ml-4 border-l-2 border-border pl-4')}>
      {children.map((childId) => {
        const childBlock = getBlock(childId);
        if (!childBlock || !isContentNode(childBlock)) return null;
        const childCount = getChildren(childId).length;
        const hasChildren = childCount > 0;
        const hasError = validationErrors.has(childId);
        const isExpanded = drawerState.expandedSections.has(childId);

        return (
          <Collapsible key={childId} open={isExpanded} onOpenChange={() => toggleSection(childId)}>
            <div
              className={cn('rounded-lg border', hasError && 'border-destructive bg-destructive/5')}
            >
              <CollapsibleTrigger className="flex w-full items-center gap-2 p-4 transition-colors hover:bg-accent/50">
                <ChevronRight
                  className={cn('h-4 w-4 transition-transform', isExpanded && 'rotate-90')}
                />
                <span className="flex-1 text-left font-medium">
                  {childBlock.block.name || childBlock.block.type.name}
                </span>
                {hasError && <AlertCircle className="h-4 w-4 text-destructive" />}
                {hasChildren && (
                  <span className="text-xs text-muted-foreground">
                    {childCount} {childCount === 1 ? 'child' : 'children'}
                  </span>
                )}
              </CollapsibleTrigger>

              <CollapsibleContent>
                <div className="space-y-4 p-4 pt-0">
                  {/* Form for this block */}
                  {hasFormFields(childBlock) && (
                    <BlockForm blockId={childId} blockType={childBlock.block.type} mode="drawer" />
                  )}

                  {/* Recursively render nested children */}
                  {hasChildren && (
                    <div className="mt-4">
                      <RecursiveFormRenderer
                        blockId={childId}
                        depth={depth + 1}
                        validationErrors={validationErrors}
                      />
                    </div>
                  )}
                </div>
              </CollapsibleContent>
            </div>
          </Collapsible>
        );
      })}
    </div>
  );
};

/* -------------------------------------------------------------------------- */
/*                                   Helpers                                  */
/* -------------------------------------------------------------------------- */

function hasFormFields(block: any): boolean {
  return (
    block &&
    block.block &&
    block.block.type &&
    block.block.type.display &&
    block.block.type.display.form &&
    block.block.type.display.form.fields &&
    Object.keys(block.block.type.display.form.fields).length > 0
  );
}
