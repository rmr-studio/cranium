'use client';

import { useReinstallTemplateMutation } from '@/components/feature-modules/dev/hooks/mutation/use-reinstall-template-mutation';
import { Button } from '@riven/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@riven/ui/dialog';
import { Input } from '@riven/ui/input';
import { Label } from '@riven/ui/label';
import { FC, FormEvent, useState } from 'react';

interface Props {
  workspaceId: string | undefined;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}

export const ReinstallTemplateDialog: FC<Props> = ({ workspaceId, open, onOpenChange }) => {
  const [templateKey, setTemplateKey] = useState('');
  const mutation = useReinstallTemplateMutation(workspaceId);

  const handleSubmit = (e: FormEvent) => {
    e.preventDefault();
    mutation.mutate(
      { templateKey: templateKey.trim() },
      {
        onSuccess: () => {
          setTemplateKey('');
          onOpenChange(false);
        },
      },
    );
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <form onSubmit={handleSubmit}>
          <DialogHeader>
            <DialogTitle>Reinstall template</DialogTitle>
            <DialogDescription>
              Removes the existing template installation and re-runs it. Existing matching entity
              types are reused.
            </DialogDescription>
          </DialogHeader>
          <div className="my-4 space-y-2">
            <Label htmlFor="dev-template-key" className="text-xs">
              Template key
            </Label>
            <Input
              id="dev-template-key"
              value={templateKey}
              onChange={(e) => setTemplateKey(e.target.value)}
              placeholder="e.g. crm"
              autoFocus
            />
          </div>
          <DialogFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={() => onOpenChange(false)}
              disabled={mutation.isPending}
            >
              Cancel
            </Button>
            <Button
              type="submit"
              disabled={!workspaceId || !templateKey.trim() || mutation.isPending}
            >
              {mutation.isPending ? 'Reinstalling...' : 'Reinstall'}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
};
