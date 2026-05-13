'use client';

import { Button } from '@cranium/ui/button';
import { FC } from "react";
import type { BlockType } from "@/lib/types/block";
import { getSortableFields, SortSpec } from "../../../util/list/list-sorting.util";

export interface ListSortControlsProps {
  blockType: BlockType;
  currentSort: SortSpec | undefined;
  onSortChange: (sort: SortSpec | undefined) => void;
}

export const ListSortControls: FC<ListSortControlsProps> = ({
  blockType,
  currentSort,
  onSortChange,
}) => {
  const sortableFields = getSortableFields(blockType);

  if (sortableFields.length === 0) {
    return null;
  }

  return (
    <div className="flex items-center gap-2 text-sm">
      <span className="text-muted-foreground">Sort by:</span>

      <select
        className="rounded border px-2 py-1 text-sm"
        value={currentSort?.by || ''}
        onChange={(e) => {
          if (!e.target.value) {
            onSortChange(undefined);
          } else {
            onSortChange({
              by: e.target.value,
              dir: currentSort?.dir || 'ASC',
            });
          }
        }}
      >
        <option value="">None</option>
        {sortableFields.map((field) => (
          <option key={field.key} value={`data.${field.key}`}>
            {field.name}
          </option>
        ))}
      </select>

      {currentSort && (
        <Button
          type="button"
          size={'xs'}
          variant={'secondary'}
          onClick={() =>
            onSortChange({
              ...currentSort,
              dir: currentSort.dir === 'ASC' ? 'DESC' : 'ASC',
            })
          }
          title={currentSort.dir === 'ASC' ? 'Sort descending' : 'Sort ascending'}
        >
          {currentSort.dir === 'ASC' ? '↑ A-Z' : '↓ Z-A'}
        </Button>
      )}

      {currentSort && (
        <Button
          type="button"
          variant={'secondary'}
          size={'xs'}
          onClick={() => onSortChange(undefined)}
        >
          Clear
        </Button>
      )}
    </div>
  );
};
