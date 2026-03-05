'use client';

import { Check, ChevronsUpDown, GalleryVerticalEnd, PlusCircle } from 'lucide-react';

import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '@riven/ui';
import { SidebarMenu, SidebarMenuButton, SidebarMenuItem } from '@/components/ui/sidebar';
import { useRouter } from 'next/navigation';
import { JSX } from 'react';

interface Choice {
  id: string;
}

export interface Action {
  title: string;
  link: string;
  icon: typeof PlusCircle;
}

interface Props<T extends Choice> {
  title: String;
  render: (value: T) => JSX.Element;
  options: T[];
  selectedOption: T | null;
  handleOptionSelection: (value: T) => void;
  additionalActions?: Action[];
}

export const OptionSwitcher = <T extends Choice>({
  options,
  selectedOption,
  handleOptionSelection,
  render,
  title,
  additionalActions,
}: Props<T>) => {
  const router = useRouter();

  return (
    <SidebarMenu>
      <SidebarMenuItem>
        <DropdownMenu modal={false}>
          <DropdownMenuTrigger asChild>
            <SidebarMenuButton
              size="lg"
              className="data-[state=open]:bg-sidebar-accent data-[state=open]:text-sidebar-accent-foreground"
            >
              <div className="flex aspect-square size-8 items-center justify-center rounded-lg bg-sidebar-primary text-sidebar-primary-foreground">
                <GalleryVerticalEnd className="size-4" />
              </div>
              {selectedOption && (
                <div className="flex flex-col gap-0.5 text-xs leading-none">
                  <span className="font-semibold">{title}</span>
                  {render(selectedOption)}
                </div>
              )}
              <ChevronsUpDown className="ml-auto" />
            </SidebarMenuButton>
          </DropdownMenuTrigger>
          <DropdownMenuContent className="mt-2 w-[15rem]" align="start">
            {options.map((option) => (
              <DropdownMenuItem
                key={option.id}
                className="text-xs"
                onSelect={() => handleOptionSelection(option)}
              >
                {render(option)}
                {option.id === selectedOption?.id && <Check className="ml-auto" />}
              </DropdownMenuItem>
            ))}
            {additionalActions &&
              additionalActions.map(({ icon: Icon, link, title }) => (
                <DropdownMenuItem
                  key={link}
                  className="mt-1 rounded-none border-t pt-2"
                  onSelect={() => {
                    router.push(link);
                  }}
                >
                  <Icon className="mr-1 size-4" />
                  <span className="text-content text-xs">{title}</span>
                </DropdownMenuItem>
              ))}
          </DropdownMenuContent>
        </DropdownMenu>
      </SidebarMenuItem>
    </SidebarMenu>
  );
};
