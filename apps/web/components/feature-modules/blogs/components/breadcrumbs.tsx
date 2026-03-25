import { CATEGORY_LABELS, type BlogCategory } from '@/lib/blog-types';
import { cn } from '@/lib/utils';
import { ChevronRight } from 'lucide-react';
import Link from 'next/link';

interface BreadcrumbsProps {
  category?: BlogCategory;
  postTitle?: string;
  variant?: 'default' | 'inverse';
}

export function Breadcrumbs({ category, postTitle, variant = 'default' }: BreadcrumbsProps) {
  const isInverse = variant === 'inverse';

  return (
    <nav aria-label="Breadcrumb" className="mb-8">
      <ol
        className={cn(
          'flex items-center gap-1.5 font-mono text-xs uppercase tracking-widest',
          isInverse ? 'text-background/50' : 'text-muted-foreground',
        )}
      >
        <li>
          <Link
            href="/"
            className={cn('transition-colors', isInverse ? 'hover:text-background' : 'hover:text-foreground')}
          >
            Home
          </Link>
        </li>
        <li aria-hidden="true"><ChevronRight className="size-3" /></li>
        <li>
          <Link
            href="/resources/blog"
            className={cn('transition-colors', isInverse ? 'hover:text-background' : 'hover:text-foreground')}
          >
            Blog
          </Link>
        </li>
        {category && (
          <>
            <li aria-hidden="true"><ChevronRight className="size-3" /></li>
            <li>
              <Link
                href={`/resources/blog/category/${category}`}
                className={cn('transition-colors', isInverse ? 'hover:text-background' : 'hover:text-foreground')}
              >
                {CATEGORY_LABELS[category]}
              </Link>
            </li>
          </>
        )}
        {postTitle && (
          <>
            <li aria-hidden="true"><ChevronRight className="size-3" /></li>
            <li className={cn('truncate max-w-48', isInverse ? 'text-background' : 'text-foreground')}>
              {postTitle}
            </li>
          </>
        )}
      </ol>
    </nav>
  );
}
