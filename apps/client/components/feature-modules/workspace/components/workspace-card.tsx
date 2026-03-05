import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Badge } from '@riven/ui/badge';
import type { WorkspaceMember } from '@/lib/types/workspace';
import Link from 'next/link';
import { FC } from 'react';

interface WorkspaceCardProps {
  membership: WorkspaceMember;
  isDefault: boolean;
}

export const WorkspaceCard: FC<WorkspaceCardProps> = ({ membership, isDefault }) => {
  const workspace = membership.workspace;

  return (
    <Link href={`/dashboard/workspace/${workspace.id}`}>
      <div className="hover:bg-accent flex w-64 cursor-pointer items-center gap-3 rounded-lg border p-4 transition-colors">
        <Avatar className="h-10 w-10">
          <AvatarImage src={workspace.avatarUrl} alt={workspace.name} />
          <AvatarFallback>{workspace.name.charAt(0).toUpperCase()}</AvatarFallback>
        </Avatar>
        <div className="flex flex-col gap-1">
          <div className="flex items-center gap-2">
            <span className="text-sm font-medium">{workspace.name}</span>
            {isDefault && (
              <Badge variant="secondary" className="text-xs">
                Default
              </Badge>
            )}
          </div>
          <span className="text-muted-foreground text-xs capitalize">{membership.role}</span>
        </div>
      </div>
    </Link>
  );
};
