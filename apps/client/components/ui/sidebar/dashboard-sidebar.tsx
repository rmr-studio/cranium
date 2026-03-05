'use client';
import { useEntityTypes } from '@/components/feature-modules/entity/hooks/query/type/use-entity-types';
import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';
import { useWorkspaceStore } from '@/components/feature-modules/workspace/provider/workspace-provider';
import { SidebarGroupProps } from '@/lib/interfaces/interface';
import type { Workspace } from '@/lib/types/workspace';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuGroup,
  DropdownMenuItem,
  DropdownMenuTrigger
} from '@riven/ui/dropdown-menu';

import { Button } from '@riven/ui/button';
import {
  Building2,
  CalendarHeart,
  CogIcon,
  Ellipsis,
  PlusCircle,
  SquareDashedMousePointer,
  TrendingUpDown,
  Workflow,
} from 'lucide-react';
import Link from 'next/link';
import { usePathname, useRouter } from 'next/navigation';

import { IconCell } from '../icon/icon-cell';
import { Skeleton } from '../skeleton';
import { AppSidebar } from './root-sidebar';
import { Action, OptionSwitcher } from './switcher';

export const DashboardSidebar = () => {
  const pathName = usePathname();
  const router = useRouter();
  const { data, isPending, isLoadingAuth } = useProfile();

  const selectedWorkspaceId = useWorkspaceStore((store) => store.selectedWorkspaceId);
  const setSelectedWorkspace = useWorkspaceStore((store) => store.setSelectedWorkspace);

  const { data: entityTypes, isLoading: isLoadingEntityTypes } =
    useEntityTypes(selectedWorkspaceId);

  const loadingUser = isPending || isLoadingAuth;

  // Derive selected workspace from user data
  const selectedWorkspace =
    data?.memberships.find((m) => m.workspace?.id === selectedWorkspaceId)?.workspace ?? null;

  const handleWorkspaceSelection = (workspace: Workspace) => {
    setSelectedWorkspace(workspace);
    router.push('/dashboard/workspace/' + workspace.id);
  };

  const switcherOptions: Action[] = [
    {
      title: 'Create Workspace',
      link: '/dashboard/workspace/new',
      icon: PlusCircle,
    },
    {
      title: 'View All Workspaces',
      link: '/dashboard/workspace',
      icon: Building2,
    },
  ];

  const DEFAULT_ICON_CLASS_PROPS = {
    className: 'size-4',
  };

  const sidebarContent: SidebarGroupProps[] = selectedWorkspace
    ? [
        {
          title: 'Overview',
          items: [
            {
              icon: <Building2 {...DEFAULT_ICON_CLASS_PROPS} />,
              hidden: false,
              title: 'Workspace',
              url: `/dashboard/workspace/${selectedWorkspace.id}`,
              isActive: pathName === `/dashboard/workspace/${selectedWorkspace.id}`,
            },
          ],
        },
        {
          title: 'Entities',
          collapsible: true,
          actions: (
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <Button variant="ghost" size="icon" className="h-6 w-6 hover:bg-accent">
                  <Ellipsis className="size-4" />
                </Button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="center">
                <DropdownMenuGroup>
                  <DropdownMenuItem
                    onClick={() =>
                      router.push(`/dashboard/workspace/${selectedWorkspace.id}/entity`)
                    }
                  >
                    <SquareDashedMousePointer />
                    <span className="ml-2 text-xs text-content">View All Entities</span>
                  </DropdownMenuItem>

                  <DropdownMenuItem
                    onClick={() =>
                      router.push(`/dashboard/workspace/${selectedWorkspace.id}/entity/environment`)
                    }
                  >
                    <Workflow />

                    <span className="ml-2 text-xs text-content">View Entity Environment</span>
                  </DropdownMenuItem>
                </DropdownMenuGroup>
              </DropdownMenuContent>
            </DropdownMenu>
          ),
          items: isLoadingEntityTypes
            ? Array.from({ length: 3 }).map((_, index) => ({
                icon: <SquareDashedMousePointer {...DEFAULT_ICON_CLASS_PROPS} />,
                hidden: false,
                title: '',
                url: '#',
                isActive: false,
                skeleton: true,
              }))
            : [
                ...(entityTypes?.slice(0, 5).map((entityType) => ({
                  icon: (
                    <IconCell
                      readonly
                      iconType={entityType.icon.type}
                      colour={entityType.icon.colour}
                    />
                  ),
                  hidden: false,
                  title: entityType.name.plural,
                  url: `/dashboard/workspace/${selectedWorkspace.id}/entity/${entityType.key}`,
                  isActive: false,
                })) ?? []),
                ...(entityTypes && entityTypes.length > 5
                  ? [
                      {
                        icon: <Ellipsis {...DEFAULT_ICON_CLASS_PROPS} />,
                        hidden: false,
                        title: `See all ${entityTypes.length}`,
                        url: `/dashboard/workspace/${selectedWorkspace.id}/entity`,
                        isActive: false,
                      },
                    ]
                  : []),
              ],
        },
        {
          title: 'Billing',
          collapsible: true,
          items: [
            {
              icon: <TrendingUpDown {...DEFAULT_ICON_CLASS_PROPS} />,
              hidden: false,
              title: 'Usage',
              url: `/dashboard/workspace/${selectedWorkspace.id}/usage`,
              isActive: pathName.startsWith(`/dashboard/usage`),
            },
            {
              icon: <CalendarHeart {...DEFAULT_ICON_CLASS_PROPS} />,
              hidden: false,
              title: 'Subscription',
              url: `/dashboard/workspace/${selectedWorkspace.id}/subscriptions`,
              isActive: pathName.startsWith(`/dashboard/subscriptions`),
            },
          ],
        },
        {
          title: 'Settings',
          collapsible: true,
          items: [
            {
              icon: <CogIcon {...DEFAULT_ICON_CLASS_PROPS} />,
              hidden: false,
              title: 'Workspace Settings',
              url: `/dashboard/workspace/${selectedWorkspace.id}/settings`,
              isActive: pathName.startsWith(
                `/dashboard/workspace/${selectedWorkspace.id}/settings`,
              ),
            },
          ],
        },
      ]
    : [];

  return (
    <AppSidebar
      header={() => {
        if (loadingUser) {
          return <Skeleton className="mx-4 mt-3 flex h-8 w-auto flex-grow" />;
        }

        if (data) {
          if (data.memberships.length === 0) {
            return (
              <>
                <Link className="mx-4 mt-3 flex w-auto flex-grow" href={'/dashboard/workspace/new'}>
                  <Button
                    variant={'outline'}
                    type="button"
                    className="w-full cursor-pointer"
                    size={'sm'}
                  >
                    Create Workspace
                  </Button>
                </Link>
                <section className="mb-8">
                  <div className="mt-6 mb-4 flex justify-center [&_svg:not([class*='text-'])]:text-muted-foreground">
                    <Building2 className="h-8 w-8" />
                  </div>
                  <div>
                    <h1 className="text-center text-sm font-semibold text-content">
                      No Workspaces Found
                    </h1>
                    <p className="text-center text-xs text-muted-foreground">
                      You currently do not have any workspaces. Create one to get started.
                    </p>
                  </div>
                </section>
              </>
            );
          }
          return (
            <OptionSwitcher
              additionalActions={switcherOptions}
              title={'Workspaces'}
              options={data.memberships.map((ws) => ws.workspace).filter((ws) => !!ws) ?? []}
              selectedOption={selectedWorkspace}
              handleOptionSelection={handleWorkspaceSelection}
              render={(ws) => <span>{ws.name}</span>}
            />
          );
        }

        return <></>;
      }}
      body={sidebarContent}
    />
  );
};
