'use client';

import { FC, useRef, useEffect } from "react";
import { get } from "lodash";
import type { BlockType } from "@/lib/types/block";
import { useBlockEdit } from "../../context/block-edit-provider";
import { formWidgetRegistry } from "./form-widget.registry";
import { cn } from '@cranium/utils';

interface BlockFormProps {
  blockId: string;
  blockType: BlockType;
  mode: 'inline' | 'drawer';
  onResize?: () => void;
}

export const BlockForm: FC<BlockFormProps> = ({ blockId, blockType, mode, onResize }) => {
  const { getDraft, updateDraft, validateField, getFieldErrors } = useBlockEdit();
  const formRef = useRef<HTMLDivElement>(null);
  const draft = getDraft(blockId);

  const { form } = blockType.display;

  // Focus first input when entering inline edit mode
  useEffect(() => {
    if (mode === 'inline' && formRef.current) {
      const firstInput = formRef.current.querySelector<HTMLElement>(
        'input:not([type="hidden"]), textarea, select',
      );
      if (firstInput) {
        firstInput.focus();
      }
    }
  }, [mode]);

  // Request resize when form mounts in inline mode
  useEffect(() => {
    if (mode === 'inline' && onResize) {
      // Delay to ensure form is fully rendered
      requestAnimationFrame(() => {
        requestAnimationFrame(() => {
          onResize();
        });
      });
    }
  }, [mode, onResize]);

  // Request resize when draft data changes (form content changes)
  useEffect(() => {
    if (mode === 'inline' && onResize && draft) {
      requestAnimationFrame(() => {
        onResize();
      });
    }
  }, [draft, mode, onResize]);

  // Tab navigation between fields and blocks
  useEffect(() => {
    const formElement = formRef.current;
    if (!formElement) return;

    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key !== 'Tab') return;

      const focusableElements = Array.from(
        formElement.querySelectorAll<HTMLElement>(
          'input:not([type="hidden"]):not([disabled]), select:not([disabled]), textarea:not([disabled]), button:not([disabled])',
        ),
      );

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];

      if (e.shiftKey) {
        // Shift+Tab on first element: navigate to previous block
        if (document.activeElement === firstElement) {
          e.preventDefault();
          window.dispatchEvent(
            new KeyboardEvent('keydown', {
              key: 'ArrowUp',
              bubbles: true,
            }),
          );
        }
      } else {
        // Tab on last element: navigate to next block
        if (document.activeElement === lastElement) {
          e.preventDefault();
          window.dispatchEvent(
            new KeyboardEvent('keydown', {
              key: 'ArrowDown',
              bubbles: true,
            }),
          );
        }
      }
    };

    formElement.addEventListener('keydown', handleKeyDown);
    return () => formElement.removeEventListener('keydown', handleKeyDown);
  }, []);

  if (!draft) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        No draft data available for this block
      </div>
    );
  }

  if (!form || !form.fields || Object.keys(form.fields).length === 0) {
    return (
      <div className="p-4 text-sm text-muted-foreground">
        No form configuration defined for this block type
      </div>
    );
  }

  return (
    <div
      ref={formRef}
      className={cn(
        'space-y-4',
        mode === 'inline' && 'rounded-sm bg-background p-4',
        mode === 'drawer' && 'space-y-3',
      )}
    >
      {Object.entries(form.fields).map(([fieldPath, fieldConfig]) => {
        const widgetMeta = formWidgetRegistry[fieldConfig.type];

        if (!widgetMeta) {
          console.warn(`Unknown widget type: ${fieldConfig.type}`);
          return (
            <div key={fieldPath} className="rounded border border-destructive p-2">
              <p className="text-sm text-destructive">Unknown widget type: {fieldConfig.type}</p>
            </div>
          );
        }

        const Widget = widgetMeta.component;
        const value = get(draft, fieldPath);
        const errors = getFieldErrors(blockId, fieldPath);

        return (
          <div key={fieldPath}>
            <Widget
              value={value !== undefined ? value : widgetMeta.defaultValue}
              onChange={(newValue) => {
                updateDraft(blockId, fieldPath, newValue);
                // Request resize after value change (deferred to allow DOM update)
                if (mode === 'inline' && onResize) {
                  requestAnimationFrame(() => {
                    onResize();
                  });
                }
              }}
              onBlur={() => {
                validateField(blockId, fieldPath);
                // Request resize after validation (errors may appear)
                if (mode === 'inline' && onResize) {
                  requestAnimationFrame(() => {
                    onResize();
                  });
                }
              }}
              label={fieldConfig.label}
              description={fieldConfig.description}
              tooltip={fieldConfig.tooltip}
              placeholder={fieldConfig.placeholder}
              options={fieldConfig.options}
              errors={errors}
            />
          </div>
        );
      })}
    </div>
  );
};
