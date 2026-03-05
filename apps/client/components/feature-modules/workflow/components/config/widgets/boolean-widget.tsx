"use client";

import type { FC } from "react";
import { Switch } from "@/components/ui/switch";
import { Label } from '@riven/ui/label';
import type { ConfigWidgetProps } from "./config-widget.types";

export const BooleanWidget: FC<ConfigWidgetProps<boolean>> = ({
  value,
  onChange,
  label,
  description,
  errors,
  disabled,
}) => {
  return (
    <div className="space-y-2">
      <div className="flex items-center justify-between">
        {label && <Label>{label}</Label>}
        <Switch
          checked={value ?? false}
          onCheckedChange={onChange}
          disabled={disabled}
        />
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
