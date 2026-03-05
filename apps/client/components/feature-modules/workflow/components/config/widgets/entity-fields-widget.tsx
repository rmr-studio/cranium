"use client";

import { useState, type FC } from "react";
import { Check, ChevronsUpDown, X, Loader2 } from "lucide-react";
import { Badge, Button, Label, Command, CommandEmpty, CommandGroup, CommandInput, CommandItem, CommandList, Popover, PopoverContent, PopoverTrigger } from '@riven/ui';
import { cn } from '@riven/utils';
import { useEntityTypeByKey } from "@/components/feature-modules/entity/hooks/query/type/use-entity-types";
import type { ConfigWidgetProps } from "./config-widget.types";

interface EntityFieldsWidgetProps extends ConfigWidgetProps<string[]> {
  /** Entity type key to get fields from - passed explicitly from NodeConfigForm */
  entityTypeKey?: string;
}

/**
 * Entity fields multi-select widget
 * Shows fields from the selected entity type schema
 * Value is an array of field keys
 *
 * Dependency: entityTypeKey must be provided by the parent form
 * The NodeConfigForm watches the ENTITY_TYPE field and passes its value
 * to this widget via the entityTypeKey prop.
 */
export const EntityFieldsWidget: FC<EntityFieldsWidgetProps> = ({
  value = [],
  onChange,
  label,
  description,
  placeholder,
  required,
  errors,
  disabled,
  workspaceId,
  entityTypeKey,
}) => {
  const [open, setOpen] = useState(false);
  const { data: entityType, isLoading, isLoadingAuth } = useEntityTypeByKey(
    entityTypeKey ?? "",
    workspaceId
  );

  const loading = isLoading || isLoadingAuth;
  const fields = entityType?.schema?.properties ?? {};
  const fieldEntries = Object.entries(fields);

  const handleSelect = (fieldKey: string) => {
    const newValue = value.includes(fieldKey)
      ? value.filter((k) => k !== fieldKey)
      : [...value, fieldKey];
    onChange(newValue);
  };

  const handleRemove = (fieldKey: string) => {
    onChange(value.filter((k) => k !== fieldKey));
  };

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
            disabled={disabled || loading || !entityTypeKey}
            className={cn(
              "w-full justify-between font-normal min-h-10 h-auto",
              errors?.length && "border-destructive",
              !value.length && "text-muted-foreground"
            )}
          >
            {loading ? (
              <span className="flex items-center gap-2">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading...
              </span>
            ) : !entityTypeKey ? (
              "Select entity type first"
            ) : value.length > 0 ? (
              <div className="flex flex-wrap gap-1">
                {value.map((fieldKey) => (
                  <Badge key={fieldKey} variant="secondary" className="mr-1">
                    {fields[fieldKey]?.label ?? fieldKey}
                    <button
                      type="button"
                      className="ml-1 hover:text-destructive"
                      onClick={(e) => {
                        e.stopPropagation();
                        handleRemove(fieldKey);
                      }}
                    >
                      <X className="h-3 w-3" />
                    </button>
                  </Badge>
                ))}
              </div>
            ) : (
              placeholder ?? "Select fields..."
            )}
            <ChevronsUpDown className="ml-2 h-4 w-4 shrink-0 opacity-50" />
          </Button>
        </PopoverTrigger>
        <PopoverContent className="w-[--radix-popover-trigger-width] p-0" align="start">
          <Command>
            <CommandInput placeholder="Search fields..." />
            <CommandList>
              <CommandEmpty>No fields found.</CommandEmpty>
              <CommandGroup>
                {fieldEntries.map(([key, field]) => (
                  <CommandItem
                    key={key}
                    value={field.label ?? key}
                    onSelect={() => handleSelect(key)}
                  >
                    <Check
                      className={cn(
                        "mr-2 h-4 w-4",
                        value.includes(key) ? "opacity-100" : "opacity-0"
                      )}
                    />
                    {field.label ?? key}
                    <span className="ml-2 text-xs text-muted-foreground">
                      {field.type}
                    </span>
                  </CommandItem>
                ))}
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
