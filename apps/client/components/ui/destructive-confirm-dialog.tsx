'use client';

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from '@/components/ui/alert-dialog';
import { Input } from '@cranium/ui/input';
import { FC, useCallback, useState } from 'react';

interface DestructiveConfirmDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  confirmValue: string;
  onConfirm: () => void;
  isPending?: boolean;
}

export const DestructiveConfirmDialog: FC<DestructiveConfirmDialogProps> = ({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  confirmValue,
  onConfirm,
  isPending,
}) => {
  const [inputValue, setInputValue] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const isMatch = inputValue === confirmValue;
  const isDisabled = !isMatch || isPending || isSubmitting;

  const handleOpenChange = (next: boolean) => {
    if (!next) {
      setInputValue('');
      setIsSubmitting(false);
    }
    onOpenChange(next);
  };

  const handleConfirm = useCallback(() => {
    if (isDisabled) return;
    setIsSubmitting(true);
    onConfirm();
  }, [isDisabled, onConfirm]);

  return (
    <AlertDialog open={open} onOpenChange={handleOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>{title}</AlertDialogTitle>
          <AlertDialogDescription>{description}</AlertDialogDescription>
        </AlertDialogHeader>
        <div className="flex flex-col gap-2 py-2">
          <p className="text-sm text-muted-foreground">
            Type <span className="font-semibold text-foreground">{confirmValue}</span> to
            confirm.
          </p>
          <Input
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            placeholder={confirmValue}
            autoComplete="off"
            aria-label={`Type "${confirmValue}" to confirm`}
          />
        </div>
        <AlertDialogFooter>
          <AlertDialogCancel disabled={isPending || isSubmitting}>Cancel</AlertDialogCancel>
          <AlertDialogAction
            disabled={isDisabled}
            onClick={(e) => {
              e.preventDefault();
              handleConfirm();
            }}
            className="bg-destructive text-destructive-foreground hover:bg-destructive/90"
          >
            {confirmLabel}
          </AlertDialogAction>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
};
