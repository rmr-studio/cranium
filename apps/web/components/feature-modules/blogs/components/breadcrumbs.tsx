import { CATEGORY_LABELS, type BlogCategory } from '@/lib/blog-types';
import { ChevronRight } from 'lucide-react';
import Link from 'next/link';

interface BreadcrumbsProps {
  category?: BlogCategory;
  postTitle?: string;
}

export function Breadcrumbs({ category, postTitle }: BreadcrumbsProps) {
  return (
    <nav aria-label="Breadcrumb" className="mb-8">
      <ol className="flex items-center gap-1.5 font-mono text-xs uppercase tracking-widest text-muted-foreground">
        <li>
          <Link href="/" className="transition-colors hover:text-foreground">Home</Link>
        </li>
        <li><ChevronRight className="size-3" /></li>
        <li>
          <Link href="/blog" className="transition-colors hover:text-foreground">Blog</Link>
        </li>
        {category && (
          <>
            <li><ChevronRight className="size-3" /></li>
            <li>
              <Link href={`/blog/category/${category}`} className="transition-colors hover:text-foreground">
                {CATEGORY_LABELS[category]}
              </Link>
            </li>
          </>
        )}
        {postTitle && (
          <>
            <li><ChevronRight className="size-3" /></li>
            <li className="truncate max-w-48 text-foreground">{postTitle}</li>
          </>
        )}
      </ol>
    </nav>
  );
}
