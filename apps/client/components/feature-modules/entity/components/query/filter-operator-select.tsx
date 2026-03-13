'use client';

import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@riven/ui/select'
import type { FilterOperator } from '@/lib/types/models/FilterOperator';
import type { OperatorOption } from './query-builder.utils';

interface FilterOperatorSelectProps {
  operators: OperatorOption[];
  value?: FilterOperator;
  onChange: (op: FilterOperator) => void;
}

export function FilterOperatorSelect({
  operators,
  value,
  onChange,
}: FilterOperatorSelectProps) {
  if (operators.length === 0) return null;

  return (
    <Select value={value ?? ''} onValueChange={(v) => onChange(v as FilterOperator)}>
      <SelectTrigger className="h-7 w-auto min-w-24 gap-1 px-2.5 text-xs font-medium">
        <SelectValue placeholder="Operator" />
      </SelectTrigger>
      <SelectContent>
        {operators.map((op) => (
          <SelectItem key={op.value} value={op.value} className="text-xs">
            {op.label}
          </SelectItem>
        ))}
      </SelectContent>
    </Select>
  );
}
