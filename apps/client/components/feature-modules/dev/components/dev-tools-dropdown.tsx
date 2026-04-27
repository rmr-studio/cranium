'use client';

import { ReinstallTemplateDialog } from '@/components/feature-modules/dev/components/reinstall-template-dialog';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { Button } from '@riven/ui/button';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from '@riven/ui/dropdown-menu';
import { FlaskConical, PackageOpen } from 'lucide-react';
import { FC, useState } from 'react';

export const DevToolsDropdown: FC = () => {
  const workspaceId = useWorkspaceStore((store) => store.selectedWorkspaceId);
  const [reinstallOpen, setReinstallOpen] = useState(false);

  const disabled = !workspaceId;

  return (
    <>
      <DropdownMenu modal={false}>
        <DropdownMenuTrigger asChild>
          <Button
            variant="ghost"
            size="icon"
            aria-label="Dev tools"
            title="Dev tools"
            className="text-amber-500 hover:text-amber-400"
          >
            <FlaskConical className="size-4" />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end" className="mt-1">
          <DropdownMenuLabel className="text-xs">Dev Tools</DropdownMenuLabel>
          <DropdownMenuSeparator />
          <DropdownMenuItem
            disabled={disabled}
            onSelect={(e) => {
              e.preventDefault();
              setReinstallOpen(true);
            }}
          >
            <PackageOpen className="size-4" />
            <span className="ml-2 text-xs text-content">Reinstall template...</span>
          </DropdownMenuItem>
        </DropdownMenuContent>
      </DropdownMenu>
      <ReinstallTemplateDialog
        workspaceId={workspaceId}
        open={reinstallOpen}
        onOpenChange={setReinstallOpen}
      />
    </>
  );
};
