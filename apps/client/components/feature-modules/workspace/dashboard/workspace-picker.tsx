'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';

import { Button } from '@riven/ui/button';
import { Input } from '@riven/ui/input';
import { PlusCircle } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useState } from 'react';
import { WorkspaceCard } from '../components/workspace-card';
import type { WorkspaceMember } from '@/lib/types/workspace';

export const WorkspacePicker = () => {
  const { data: user, isPending } = useProfile();

  const [workspaceSearch, setWorkspaceSearch] = useState<string>('');
  const [renderedWorkspaces, setRenderedWorkspaces] = useState<WorkspaceMember[]>([]);
  useEffect(() => {
    if (user?.memberships) {
      setRenderedWorkspaces(
        user.memberships.filter((org) =>
          org.workspace?.name.toLowerCase().includes(workspaceSearch?.toLowerCase() || ''),
        ),
      );
    } else {
      setRenderedWorkspaces([]);
    }
  }, [user, workspaceSearch]);

  return (
    <>
      <section className="mt-6 flex space-x-4">
        <Input
          className="w-full max-w-sm"
          placeholder="Search Workspaces"
          value={workspaceSearch ?? ''}
          onChange={(e) => {
            setWorkspaceSearch(e.target.value);
            if (user?.memberships) {
              setRenderedWorkspaces(
                user.memberships.filter((org) =>
                  org.workspace?.name.toLowerCase().includes(e.target.value.toLowerCase()),
                ),
              );
            }
          }}
        />

        <Link href={'/dashboard/workspace/new'}>
          <Button variant={'outline'} size={'sm'} className="h-full cursor-pointer">
            <PlusCircle className="mr-2" />
            Create Workspace
          </Button>
        </Link>
      </section>
      <section className="mt-8 flex flex-shrink-0 flex-wrap space-x-4">
        {renderedWorkspaces.length > 0 ? (
          <>
            {renderedWorkspaces.map(
              (org) =>
                org.workspace?.id && (
                  <WorkspaceCard
                    key={org.workspace.id}
                    membership={org}
                    isDefault={user?.defaultWorkspace?.id === org.workspace.id}
                  />
                ),
            )}
          </>
        ) : isPending ? (
          <>Loading... </>
        ) : (
          <>No Workspaces found</>
        )}
      </section>
    </>
  );
};
