'use client';

import { FC, Fragment, useCallback, useMemo, useState } from 'react';

import {
  Drawer,
  DrawerClose,
  DrawerContent,
  DrawerDescription,
  DrawerFooter,
  DrawerHeader,
  DrawerTitle,
  DrawerTrigger,
} from '@/components/ui/drawer';
import { useMediaQuery } from '@cranium/hooks';
import { Button } from '@cranium/ui/button';
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuTrigger } from '@cranium/ui/dropdown-menu';
import type { ClassNameProps } from '@cranium/utils';
import { cn } from '@cranium/utils';
import Link from 'next/link';
import {
  Breadcrumb,
  BreadcrumbEllipsis,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from './breadcrumb';

export interface BreadCrumbTrail {
  label: string;
  href?: string;
  truncate?: boolean;
  active?: boolean; // NEW
}

type Variant = 'auto' | 'desktop' | 'mobile';

interface Props extends ClassNameProps {
  items: BreadCrumbTrail[];
  variant?: Variant;
}

type Group =
  | {
      type: 'item';
      item: BreadCrumbTrail;
      index: number;
    }
  | {
      type: 'truncated';
      items: BreadCrumbTrail[];
      startIndex: number;
    };

export const BreadCrumbGroup: FC<Props> = ({ items, variant = 'auto', className }) => {
  const [openGroupIndex, setOpenGroupIndex] = useState<number | null>(null);
  const isDesktop = useMediaQuery('(min-width: 768px)');

  // Determine rendering mode
  const mode = useMemo(() => {
    return variant === 'auto' ? (isDesktop ? 'desktop' : 'mobile') : variant;
  }, [variant, isDesktop]);

  // Group consecutive truncated items together
  const groups = useMemo((): Group[] => {
    const result: Group[] = [];
    let currentTruncated: BreadCrumbTrail[] = [];
    let groupStartIndex = 0;

    items.forEach((item, index) => {
      if (item.truncate === true) {
        if (currentTruncated.length === 0) {
          groupStartIndex = index;
        }
        currentTruncated.push(item);
      } else {
        if (currentTruncated.length > 0) {
          result.push({
            type: 'truncated',
            items: [...currentTruncated],
            startIndex: groupStartIndex,
          });
          currentTruncated = [];
        }
        result.push({
          type: 'item',
          item,
          index,
        });
      }
    });

    if (currentTruncated.length > 0) {
      result.push({
        type: 'truncated',
        items: [...currentTruncated],
        startIndex: groupStartIndex,
      });
    }

    return result;
  }, [items]);

  const handleGroupToggle = useCallback((groupIndex: number, isOpen: boolean) => {
    setOpenGroupIndex(isOpen ? groupIndex : null);
  }, []);

  return (
    <Breadcrumb className={cn(className)}>
      <BreadcrumbList>
        {groups.map((group, groupIndex) => {
          const isLast = groupIndex === groups.length - 1;

          if (group.type === 'truncated') {
            return (
              <Fragment key={`truncated-${group.startIndex}`}>
                <BreadcrumbItem>
                  {mode === 'desktop' ? (
                    <DropdownMenu
                      open={openGroupIndex === groupIndex}
                      onOpenChange={(open) => handleGroupToggle(groupIndex, open)}
                    >
                      <DropdownMenuTrigger
                        className="flex items-center gap-1"
                        aria-label="Show hidden breadcrumb items"
                      >
                        <BreadcrumbEllipsis className="size-4" />
                      </DropdownMenuTrigger>
                      <DropdownMenuContent align="start">
                        {group.items.map((item, itemIndex) =>
                          item.active ? (
                            <DropdownMenuItem
                              key={`${group.startIndex}-${itemIndex}`}
                              disabled // active item shouldn’t be clickable
                            >
                              <span className="font-medium text-foreground">{item.label}</span>
                            </DropdownMenuItem>
                          ) : (
                            <DropdownMenuItem key={`${group.startIndex}-${itemIndex}`} asChild>
                              <Link href={item.href ?? '#'}>{item.label}</Link>
                            </DropdownMenuItem>
                          ),
                        )}
                      </DropdownMenuContent>
                    </DropdownMenu>
                  ) : (
                    <Drawer
                      open={openGroupIndex === groupIndex}
                      onOpenChange={(open) => handleGroupToggle(groupIndex, open)}
                    >
                      <DrawerTrigger aria-label="Show hidden breadcrumb items">
                        <BreadcrumbEllipsis className="h-4 w-4" />
                      </DrawerTrigger>
                      <DrawerContent>
                        <DrawerHeader className="text-left">
                          <DrawerTitle>Navigate to</DrawerTitle>
                          <DrawerDescription>Select a page to navigate to.</DrawerDescription>
                        </DrawerHeader>
                        <div className="grid gap-1 px-4">
                          {group.items.map((item, itemIndex) =>
                            item.active ? (
                              <span
                                key={`${group.startIndex}-${itemIndex}`}
                                className="py-1 text-sm font-medium text-foreground"
                              >
                                {item.label}
                              </span>
                            ) : (
                              <Link
                                key={`${group.startIndex}-${itemIndex}`}
                                href={item.href ?? '#'}
                                className="py-1 text-sm hover:underline"
                              >
                                {item.label}
                              </Link>
                            ),
                          )}
                        </div>
                        <DrawerFooter className="pt-4">
                          <DrawerClose asChild>
                            <Button variant="outline">Close</Button>
                          </DrawerClose>
                        </DrawerFooter>
                      </DrawerContent>
                    </Drawer>
                  )}
                </BreadcrumbItem>
                {!isLast && <BreadcrumbSeparator />}
              </Fragment>
            );
          }

          // Regular visible item
          const { item } = group;
          return (
            <Fragment key={`item-${group.index}`}>
              <BreadcrumbItem>
                {item.active ? (
                  <BreadcrumbPage className="max-w-20 truncate md:max-w-none">
                    {item.label}
                  </BreadcrumbPage>
                ) : item.href ? (
                  <BreadcrumbLink asChild className="max-w-20 truncate md:max-w-none">
                    <Link href={item.href}>{item.label}</Link>
                  </BreadcrumbLink>
                ) : (
                  <BreadcrumbPage className="max-w-20 truncate md:max-w-none">
                    {item.label}
                  </BreadcrumbPage>
                )}
              </BreadcrumbItem>
              {!isLast && <BreadcrumbSeparator />}
            </Fragment>
          );
        })}
      </BreadcrumbList>
    </Breadcrumb>
  );
};
