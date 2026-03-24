'use client';

import type { BlogPostMeta } from '@/lib/blog-types';
import { Search, X } from 'lucide-react';
import Fuse from 'fuse.js';
import { useMemo, useState } from 'react';
import { BlogFeed } from './blog-feed';

interface BlogSearchProps {
  posts: BlogPostMeta[];
}

export function BlogSearch({ posts }: BlogSearchProps) {
  const [query, setQuery] = useState('');

  const fuse = useMemo(
    () =>
      new Fuse(posts, {
        keys: ['title', 'description', 'tags', 'category'],
        threshold: 0.3,
      }),
    [posts],
  );

  const results = query.length > 0 ? fuse.search(query).map((r) => r.item) : posts;

  return (
    <div>
      <div className="relative mb-8">
        <Search className="absolute left-3 top-1/2 size-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search posts..."
          aria-label="Search blog posts"
          className="w-full rounded-sm border border-border bg-transparent py-2.5 pl-10 pr-10 text-sm text-foreground placeholder:text-muted-foreground focus:border-muted-foreground focus:outline-none"
        />
        {query && (
          <button
            onClick={() => setQuery('')}
            className="absolute right-3 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            aria-label="Clear search"
          >
            <X className="size-4" />
          </button>
        )}
      </div>
      {query && results.length === 0 ? (
        <div className="py-12 text-center">
          <p className="text-muted-foreground">No posts matching &ldquo;{query}&rdquo;.</p>
          <button onClick={() => setQuery('')} className="mt-2 text-sm text-foreground underline underline-offset-4">
            Clear search
          </button>
        </div>
      ) : (
        <BlogFeed posts={results} />
      )}
    </div>
  );
}
