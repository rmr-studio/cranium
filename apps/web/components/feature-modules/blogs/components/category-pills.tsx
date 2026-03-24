'use client';

import { CATEGORY_LABELS, type BlogCategory } from '@/lib/blog-types';
import { cn } from '@/lib/utils';
import Link from 'next/link';
import { usePathname } from 'next/navigation';

interface CategoryPillsProps {
  categories: { slug: BlogCategory; count: number }[];
}

export function CategoryPills({ categories }: CategoryPillsProps) {
  const pathname = usePathname();
  const isAll = pathname === '/blog';

  return (
    <div className="mb-12">
      <h2 className="mb-4 font-[family-name:var(--font-instrument-serif)] text-2xl italic text-muted-foreground">
        Browse by category:
      </h2>
      <nav className="flex flex-wrap gap-2" aria-label="Blog categories">
        <Link
          href="/blog"
          aria-current={isAll ? 'page' : undefined}
          className={cn(
            'rounded-sm border px-3 py-1.5 font-mono text-xs font-bold uppercase tracking-widest transition-colors',
            isAll
              ? 'border-foreground bg-foreground text-background'
              : 'border-border text-muted-foreground hover:border-muted-foreground hover:text-foreground',
          )}
        >
          All
        </Link>
        {categories.map((cat) => {
          const isActive = pathname === `/blog/category/${cat.slug}`;
          return (
            <Link
              key={cat.slug}
              href={`/blog/category/${cat.slug}`}
              aria-current={isActive ? 'page' : undefined}
              className={cn(
                'rounded-sm border px-3 py-1.5 font-mono text-xs font-bold uppercase tracking-widest transition-colors',
                isActive
                  ? 'border-foreground bg-foreground text-background'
                  : 'border-border text-muted-foreground hover:border-muted-foreground hover:text-foreground',
              )}
            >
              {CATEGORY_LABELS[cat.slug]} [{cat.count}]
            </Link>
          );
        })}
      </nav>
    </div>
  );
}
