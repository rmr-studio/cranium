import { cn, toTitleCase } from '@riven/utils';
import type { ClassNameProps } from '@riven/utils';
import { Search } from 'lucide-react';
import { Fragment, useEffect, useState } from 'react';
import { Input } from './input';

type DotPrefix<T extends string, U extends string> = T extends '' ? U : `${T}.${U}`;

type NestedKeys<T, Prev extends string = ''> = T extends object
  ? {
      [K in keyof T & string]: T[K] extends object
        ? DotPrefix<Prev, K> | NestedKeys<T[K], DotPrefix<Prev, K>>
        : DotPrefix<Prev, K>;
    }[keyof T & string]
  : never;

interface Props<T> extends ClassNameProps {
  items?: T[];
  // Allow for nested keys using dot notation
  keys: NestedKeys<T>[];
  title: string;
  render: (item: T) => React.ReactNode;
  onQueryChange?: (query: string) => void;
}

function getValueByPath(obj: any, path: string): unknown {
  return path.split('.').reduce((acc, part) => acc?.[part], obj);
}

export const FilterList = <T extends unknown>({
  items,
  render,
  keys,
  title,
  onQueryChange,
  className,
}: Props<T>) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [filteredItems, setFilteredItems] = useState(items ?? []);

  useEffect(() => {
    if (!items) return;

    onQueryChange?.(searchQuery);
    if (searchQuery === '') {
      setFilteredItems(items);
      return;
    }

    const lowerCaseQuery = searchQuery.toLowerCase();
    const filtered = items.filter((item) =>
      keys.some((path) => {
        const value = getValueByPath(item, path);
        return typeof value === 'string' && value.toLowerCase().includes(lowerCaseQuery);
      }),
    );

    setFilteredItems(filtered);
  }, [searchQuery]);

  return (
    <section>
      <div className="relative max-w-md">
        <Search className="absolute top-1/2 left-3 h-4 w-4 -translate-y-1/2 transform text-muted-foreground" />
        <Input
          placeholder="Search clients..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          className="pl-10"
        />
      </div>
      <div className="space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-medium text-foreground">
            All {toTitleCase(title)} ({items?.length ?? 0})
          </h2>
        </div>

        {filteredItems.length === 0 ? (
          <div className="py-12 text-center">
            <p className="text-muted-foreground">
              {searchQuery ? `No results found matching your search.` : 'No results yet.'}
            </p>
          </div>
        ) : (
          <section className={cn('', className)}>
            {filteredItems.map((item, index) => (
              <Fragment key={index}>{render(item)}</Fragment>
            ))}
          </section>
        )}
      </div>
    </section>
  );
};

export default FilterList;
