"use client";

import { type FC, useCallback } from "react";
import { Plus, X } from "lucide-react";
import { Input, Label, Button } from '@riven/ui';
import type { ConfigWidgetProps } from "./config-widget.types";

interface KeyValuePair {
  key: string;
  value: string;
}

/**
 * Widget for KEY_VALUE field type - allows editing key-value pairs
 * Stores value as Record<string, string> or array of {key, value} objects
 */
export const KeyValueWidget: FC<ConfigWidgetProps<Record<string, string>>> = ({
  value,
  onChange,
  label,
  description,
  required,
  errors,
  disabled,
}) => {
  // Convert record to array for easier manipulation
  const pairs: KeyValuePair[] = value
    ? Object.entries(value).map(([key, val]) => ({ key, value: val }))
    : [];

  const updatePairs = useCallback(
    (newPairs: KeyValuePair[]) => {
      // Convert back to record, filtering out empty keys
      const record: Record<string, string> = {};
      newPairs.forEach((pair) => {
        if (pair.key.trim()) {
          record[pair.key] = pair.value;
        }
      });
      onChange(record);
    },
    [onChange]
  );

  const handleKeyChange = (index: number, newKey: string) => {
    const newPairs = [...pairs];
    newPairs[index] = { ...newPairs[index], key: newKey };
    updatePairs(newPairs);
  };

  const handleValueChange = (index: number, newValue: string) => {
    const newPairs = [...pairs];
    newPairs[index] = { ...newPairs[index], value: newValue };
    updatePairs(newPairs);
  };

  const handleAdd = () => {
    updatePairs([...pairs, { key: "", value: "" }]);
  };

  const handleRemove = (index: number) => {
    const newPairs = pairs.filter((_, i) => i !== index);
    updatePairs(newPairs);
  };

  return (
    <div className="space-y-2">
      {label && (
        <Label>
          {label}
          {required && <span className="text-destructive ml-1">*</span>}
        </Label>
      )}
      <div className="space-y-2">
        {pairs.map((pair, index) => (
          <div key={index} className="flex gap-2 items-center">
            <Input
              placeholder="Key"
              value={pair.key}
              onChange={(e) => handleKeyChange(index, e.target.value)}
              disabled={disabled}
              className={`flex-1 ${errors?.length ? "border-destructive" : ""}`}
            />
            <Input
              placeholder="Value"
              value={pair.value}
              onChange={(e) => handleValueChange(index, e.target.value)}
              disabled={disabled}
              className="flex-1"
            />
            <Button
              type="button"
              variant="ghost"
              size="icon"
              onClick={() => handleRemove(index)}
              disabled={disabled}
              className="h-9 w-9 shrink-0"
            >
              <X className="h-4 w-4" />
            </Button>
          </div>
        ))}
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={handleAdd}
          disabled={disabled}
          className="w-full"
        >
          <Plus className="h-4 w-4 mr-2" />
          Add Entry
        </Button>
      </div>
      {description && !errors?.length && (
        <p className="text-xs text-muted-foreground">{description}</p>
      )}
      {errors?.map((error, i) => (
        <p key={i} className="text-xs text-destructive">
          {error}
        </p>
      ))}
    </div>
  );
};
