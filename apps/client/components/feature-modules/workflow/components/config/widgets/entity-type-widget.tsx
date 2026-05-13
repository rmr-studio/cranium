"use client";

import { useState, type FC } from "react";
import { Check, ChevronsUpDown, Loader2 } from "lucide-react";
import { Button } from '@cranium/ui/button';
import { Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList } from '@cranium/ui/command';
import { Label } from '@cranium/ui/label';
import { Popover, PopoverContent, PopoverTrigger } from '@cranium/ui/popover';
import { cn } from '@cranium/utils';
import { useEntityTypes } from "@/components/feature-modules/entity/hooks/query/type/use-entity-types";
import { ICON_REGISTRY } from "@/components/ui/icon/icon-mapper";
import type { ConfigWidgetProps } from "./config-widget.types";

/**
 * Entity type selection widget with searchable dropdown
 * Shows entity types from the workspace with icons
 * Value is the entity type key (string)
 */
export const EntityTypeWidget: FC<ConfigWidgetProps<string>> = ({
  value,
  onChange,
  label,
  description,
  placeholder,
  required,
  errors,
  disabled,
  workspaceId,
}) => {
  const [open, setOpen] = useState(false);
  const { data: entityTypes, isLoading, isLoadingAuth } = useEntityTypes(workspaceId);

  const selectedType = entityTypes?.find((et) => et.key === value);
  const loading = isLoading || isLoadingAuth;

  // Get icon component from registry, fallback to null
  const getIconComponent = (iconType: string | undefined) => {
    if (!iconType) return null;
    return ICON_REGISTRY[iconType as keyof typeof ICON_REGISTRY] ?? null;
  };

  const SelectedIcon = selectedType?.icon?.type
    ? getIconComponent(selectedType.icon.type)
    : null;

  return (
    <div className="space-y-2">
      {label && (
        <Label>
          {label}
          {required && <span className="text-destructive ml-1">*</span>}
        </Label>
      )}
      <Popover open={open} onOpenChange={setOpen}>
        <PopoverTrigger asChild>
          <Button
            variant="outline"
            role="combobox"
            aria-expanded={open}
            disabled={disabled || loading}
            className={cn(
              "w-full justify-between font-normal",
              errors?.length && "border-destructive",
              !value && "text-muted-foreground"
            )}
          >
            {loading ? (
              <span className="flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading...
              </span>
            ) : selectedType ? (
              <span className="flex items-center gap-2">
                {SelectedIcon && <SelectedIcon className="h-4 w-4" />}
                {selectedType.name.singular}
              </span>
            ) : (
              placeholder ?? "Select entity type..."
            )}
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
          <Command>
            <CommandInput placeholder="Search entity types..." />
            <CommandList>
              <CommandEmpty>No entity types found.</CommandEmpty>
              <CommandGroup>
                {entityTypes?.map((et) => {
                  const ItemIcon = getIconComponent(et.icon?.type);
                  return (
                    <CommandItem
                      key={et.key}
                      value={et.name.singular}
                      onSelect={() => {
                        onChange(et.key);
                        setOpen(false);
                      }}
                    >
                      {ItemIcon && <ItemIcon className="mr-2 h-4 w-4" />}
                      {et.name.singular}
                      <Check
                        className={cn(
                          "ml-auto h-4 w-4",
                          value === et.key ? "opacity-100" : "opacity-0"
                        )}
                      />
                    </CommandItem>
                  );
                })}
              </CommandGroup>
            </CommandList>
          </Command>
        </PopoverContent>
      </Popover>
      {description && !errors?.length && (
        <p className="text-xs text-muted-foreground">{description}</p>
      )}
      {errors?.map((error, i) => (
        <p key={i} className="text-xs text-destructive">{error}</p>
      ))}
    </div>
  );
};
