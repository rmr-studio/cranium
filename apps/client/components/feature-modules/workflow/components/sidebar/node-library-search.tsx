"use client";

import { Search } from "lucide-react";
import { Input } from '@riven/ui';
import { cn } from '@riven/utils';

export interface NodeLibrarySearchProps {
    /** Current search query value */
    value: string;
    /** Callback when search value changes */
    onChange: (value: string) => void;
    /** Optional additional className */
    className?: string;
}

/**
 * Search input for filtering nodes in the library sidebar
 * Includes search icon prefix and placeholder text
 */
export function NodeLibrarySearch({ value, onChange, className }: NodeLibrarySearchProps) {
    return (
        <div className={cn("relative", className)}>
            <Search className="absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
                type="text"
                placeholder="Search nodes..."
                value={value}
                onChange={(e) => onChange(e.target.value)}
                className="pl-9"
            />
        </div>
    );
}
