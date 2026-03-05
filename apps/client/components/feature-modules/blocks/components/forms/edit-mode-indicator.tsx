'use client';

import { Button } from '@riven/ui';
import { cn } from '@riven/utils';
import { AnimatePresence, motion } from "framer-motion";
import { AlertCircle, Check, Edit3, FileEdit, Layout, X } from "lucide-react";
import { FC, useState } from "react";
import { useBlockEdit } from "../../context/block-edit-provider";
import { useBlockEnvironment } from "../../context/block-environment-provider";
import { useLayoutChange } from "../../context/layout-change-provider";
import { useLayoutHistory } from "../../context/layout-history-provider";
import { useLayoutKeyboardShortcuts } from "../../hooks/use-layout-keyboard-shortcuts";
import type { BlockNode } from "@/lib/types/block";

export const EditModeIndicator: FC = () => {
  const { getEditingCount, hasActualChanges, saveAllEdits, discardAllEdits, exitAllSessions } =
    useBlockEdit();
  const { isInitialized } = useBlockEnvironment();
  const {
    saveLayoutChanges,
    discardLayoutChanges,
    saveStatus,
    conflictData,
    resolveConflict,
    suppressEditModeTracking,
  } = useLayoutChange();
  const { hasContentChanges, hasLayoutChanges } = useLayoutHistory();

  const editingCount = getEditingCount();
  const hasDataChanges = hasActualChanges();
  const canSave = hasDataChanges || hasLayoutChanges || hasContentChanges;
  const totalChanges = editingCount + (hasLayoutChanges ? 1 : 0) + (hasContentChanges ? 1 : 0);

  const [isSaving, setIsSaving] = useState(false);

  const handleSaveAll = async () => {
    if (isSaving || saveStatus === 'saving' || saveStatus === 'conflict') {
      return;
    }

    // If no actual changes, just exit all sessions silently
    if (!canSave) {
      // Suppress tracking while exiting sessions to prevent false positives
      suppressEditModeTracking(true);
      exitAllSessions();

      /**
       * Triple RAF timing pattern to re-enable layout tracking after grid settles.
       *
       * Why three frames?
       * 1. Frame 1: React schedules state updates from exitAllSessions()
       * 2. Frame 2: React commits DOM changes (form unmounts, display mounts)
       * 3. Frame 3: GridStack reflows/resizes widgets based on new content dimensions
       *
       * Race prevented: Without this delay, we'd re-enable tracking before
       * GridStack's 'change' event fires from the resize, causing false layout changes.
       *
       * Alternative: Could use MutationObserver + ResizeObserver to deterministically
       * detect when GridStack completes layout, but triple-RAF is simpler and reliable
       * for this use case (matches pattern in use-panel-edit-mode.ts lines 86-94).
       */
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            suppressEditModeTracking(false);
          });
        });
      });
      return;
    }

    setIsSaving(true);
    try {
      let contentChanges: Map<string, BlockNode> | undefined;

      // Step 1: Prepare content changes from active edit sessions
      if (hasDataChanges) {
        const result = await saveAllEdits();
        if (!result.success) {
          console.error('Failed to prepare content changes (validation failed)');
          setIsSaving(false);
          return;
        }
        contentChanges = result.changes;
      }

      // Step 2: Save everything to backend atomically
      // This includes: layout changes + structural operations + content changes
      if (hasLayoutChanges || hasContentChanges || (contentChanges && contentChanges.size > 0)) {
        const success = await saveLayoutChanges(contentChanges);
        if (!success) {
          console.error('Failed to save to backend');
          setIsSaving(false);
          return;
        }
      }

      // Step 3: Clean up ALL edit sessions (both dirty and clean)
      // Suppress tracking while exiting to prevent false positives from dimension changes
      suppressEditModeTracking(true);
      exitAllSessions();

      /**
       * Triple RAF timing pattern to re-enable layout tracking after grid settles.
       * See detailed explanation above in the !canSave path (lines 47-61).
       * Same timing requirements apply: wait for React commit + GridStack reflow.
       */
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          requestAnimationFrame(() => {
            suppressEditModeTracking(false);
          });
        });
      });
    } catch (error) {
      console.error('Error saving all changes:', error);
    } finally {
      setIsSaving(false);
    }
  };

  // Set up keyboard shortcut for save (Ctrl/Cmd+S)
  const canSaveViaShortcut =
    !isSaving &&
    saveStatus !== 'saving' &&
    saveStatus !== 'conflict' &&
    (hasDataChanges || hasLayoutChanges || hasContentChanges);

  useLayoutKeyboardShortcuts(canSaveViaShortcut ? handleSaveAll : undefined);

  // Don't show indicator during initialization to prevent false positives
  // from widget sync operations
  const shouldShow = isInitialized && totalChanges > 0;

  const handleDiscardAll = () => {
    // Suppress layout change tracking during edit mode exit to prevent
    // false positives from blocks resizing back to display view
    suppressEditModeTracking(true);

    discardLayoutChanges();

    // Then exit all edit sessions (discards local drafts)
    exitAllSessions();

    /**
     * Triple RAF timing pattern to re-enable layout tracking after grid settles.
     * See detailed explanation in handleSaveAll's !canSave path (lines 47-61).
     * Same timing requirements: React commit + GridStack reflow must complete
     * before re-enabling tracking to prevent false 'change' events.
     */
    requestAnimationFrame(() => {
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          suppressEditModeTracking(false);
        });
      });
    });
  };

  return (
    <AnimatePresence>
      {shouldShow && (
        <motion.div
          key="edit-indicator"
          initial={{ opacity: 0, y: -20 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: -20 }}
          className={cn(
            'fixed top-4 left-1/2 z-50 -translate-x-1/2',
            'flex items-center gap-3 rounded-lg px-4 py-2.5 shadow-lg',
            'bg-primary text-primary-foreground dark:bg-card',
            'border border-primary shadow dark:border-primary/30',
          )}
        >
          {/* Status indicators */}
          <div className="flex items-center gap-2">
            {editingCount > 0 && (
              <>
                <Edit3 className="h-4 w-4" />
                <span className="font-medium">
                  {editingCount} block{editingCount !== 1 ? 's' : ''} editing
                </span>
              </>
            )}
            {editingCount > 0 && (hasLayoutChanges || hasContentChanges) && (
              <span className="text-primary-foreground/60">•</span>
            )}
            {hasLayoutChanges && (
              <>
                <Layout className="h-4 w-4" />
                <span className="font-medium text-neutral-200">Layout modified</span>
              </>
            )}
            {hasLayoutChanges && hasContentChanges && (
              <span className="text-primary-foreground/60">•</span>
            )}
            {hasContentChanges && (
              <>
                <FileEdit className="h-4 w-4" />
                <span className="font-medium text-neutral-200">Content modified</span>
              </>
            )}
          </div>

          {(hasDataChanges || hasLayoutChanges || hasContentChanges) && (
            <>
              <div className="h-4 w-px bg-neutral-400" />
              <div className="flex items-center gap-2">
                <AlertCircle className="h-3.5 w-3.5 text-edit" />
                <span className="text-sm text-edit">Unsaved changes</span>
              </div>
            </>
          )}

          <div className="h-4 w-px bg-neutral-400" />

          {/* Action buttons */}
          <div className="flex items-center gap-2">
            <Button
              size="sm"
              onClick={handleSaveAll}
              variant={'outline'}
              className="text-primary"
              disabled={isSaving || saveStatus === 'saving' || saveStatus === 'conflict'}
              title={canSave ? 'Save all changes' : 'Exit edit mode'}
            >
              <Check className="mr-1 h-3.5 w-3.5" />
              {isSaving || saveStatus === 'saving'
                ? 'Saving...'
                : saveStatus === 'conflict'
                  ? 'Conflict!'
                  : canSave
                    ? 'Save All'
                    : 'Exit Edit Mode'}
            </Button>
            <Button
              size="sm"
              variant="destructive"
              className="bg-destructive/50"
              onClick={handleDiscardAll}
              disabled={isSaving || saveStatus === 'saving'}
              title="Discard all unsaved changes"
            >
              <X className="mr-1 h-3.5 w-3.5" />
              Discard All
            </Button>
          </div>
        </motion.div>
      )}

      {/* Conflict Resolution Modal */}
      {saveStatus === 'conflict' && conflictData && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          exit={{ opacity: 0 }}
          className="fixed inset-0 z-[60] flex items-center justify-center bg-black/50"
          onClick={(e) => {
            if (e.target === e.currentTarget) {
              resolveConflict('cancel');
            }
          }}
        >
          <motion.div
            initial={{ scale: 0.95, opacity: 0 }}
            animate={{ scale: 1, opacity: 1 }}
            exit={{ scale: 0.95, opacity: 0 }}
            className="mx-4 w-full max-w-md rounded-lg border border-border bg-background p-6 shadow-xl"
          >
            <h3 className="mb-2 flex items-center gap-2 text-lg font-semibold">
              <AlertCircle className="h-5 w-5 text-yellow-500" />
              Layout Conflict Detected
            </h3>
            <p className="mb-4 text-sm text-muted-foreground">
              Another user ({conflictData.lastModifiedBy}) saved changes while you were editing. You
              can keep your changes or use their version.
            </p>
            <div className="flex flex-col gap-2">
              <Button
                variant="default"
                onClick={() => resolveConflict('keep-mine')}
                className="w-full"
              >
                Keep My Changes
              </Button>
              <Button
                variant="secondary"
                onClick={() => resolveConflict('use-theirs')}
                className="w-full"
              >
                Use Their Version
              </Button>
              <Button variant="ghost" onClick={() => resolveConflict('cancel')} className="w-full">
                Cancel
              </Button>
            </div>
          </motion.div>
        </motion.div>
      )}
    </AnimatePresence>
  );
};
