# Blog & SEO Infrastructure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-dcranium-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build a complete MDX blog system with SEO infrastructure, topic clusters, and featured blog sections on the landing page — all ungated, optimized for search engines and AI citation.

**Architecture:** MDX files in `content/blog/` are processed at build time via `next-mdx-remote/rsc`. A `lib/blog.ts` utility layer reads and parses all posts (wrapped with `React.cache()` to deduplicate calls). Pages at `/blog`, `/blog/[slug]`, and `/blog/category/[category]` render the content with full SEO (JSON-LD, OG tags, sitemap, RSS). The blog index uses a featured hero + category pills + Hex-style text-only feed. The landing page gets a featured posts section. Dynamic OG images are generated via `next/og` (built into Next.js 16, no extra dep).

**Tech Stack:** Next.js 16 (App Router), next-mdx-remote/rsc, rehype-pretty-code (shiki), rehype-slug, rehype-autolink-headings, Fuse.js, Vitest

---

## File Structure

```
apps/web/
  content/
    blog/
      intercom-vs-zendesk.mdx              # Sample post (tool comparison)
      tracking-churn-across-tools.mdx       # Sample post (operational intelligence)
  app/
    blog/
      page.tsx                              # Blog index (featured hero + pills + feed)
      [slug]/
        page.tsx                            # Blog post page (article + TOC + related)
      category/
        [category]/
          page.tsx                          # Category filtered view
      layout.tsx                            # Blog section layout (shared chrome)
      rss.xml/
        route.ts                            # RSS feed generation
    api/
      og/
        route.tsx                           # Dynamic OG image generation (Node.js runtime, next/og)
    page.tsx                                # Landing page (MODIFY: remove 'use client', add featured posts)
    robots.ts                               # MODIFY: allow AI crawlers
    sitemap.ts                              # MODIFY: add blog routes
  lib/
    blog.ts                                 # Blog utilities (getAllPosts, getPostBySlug, etc.)
    blog-types.ts                           # TypeScript types for blog data
  components/
    feature-modules/
      blog/
        components/
          blog-feed.tsx                     # Hex-style text-only feed list
          blog-hero.tsx                     # Featured post hero with cover image
          blog-search.tsx                   # Client-side Fuse.js search
          category-pills.tsx                # Horizontal category pill buttons
          featured-posts.tsx                # Landing page featured posts section
          reading-progress.tsx              # Scroll progress bar
          related-posts.tsx                 # Tag-based related posts
          table-of-contents.tsx             # Sticky TOC sidebar + mobile dropdown
          breadcrumbs.tsx                   # Breadcrumb navigation
        mdx/
          mdx-components.tsx                # MDX component overrides (headings, code, tables)
          comparison-table.tsx              # Interactive comparison table MDX component
          code-block.tsx                    # Code block with copy-to-clipboard
  hooks/
    use-reading-progress.ts                 # Scroll progress hook
  __tests__/
    lib/
      blog.test.ts                          # Unit tests for blog utilities
    api/
      og.test.ts                            # Tests for OG image route
```

---

### Task 1: Install Dependencies

**Files:**

- Modify: `apps/web/package.json`

- [ ] **Step 1: Install MDX and rehype packages**

```bash
cd apps/web && pnpm add next-mdx-remote gray-matter reading-time rehype-slug rehype-autolink-headings rehype-pretty-code shiki
```

- [ ] **Step 2: Install search and test packages**

Note: OG image generation uses `next/og` which is built into Next.js 16 — no extra dep needed.

```bash
cd apps/web && pnpm add fuse.js
cd apps/web && pnpm add -D vitest @vitejs/plugin-react jsdom @testing-library/react
```

- [ ] **Step 3: Add test script to package.json**

Add to `scripts` in `apps/web/package.json`:

```json
"test": "vitest run",
"test:watch": "vitest"
```

- [ ] **Step 4: Create Vitest config**

Create `apps/web/vitest.config.ts`:

```typescript
import { defineConfig } from 'vitest/config';
import path from 'path';

export default defineConfig({
  test: {
    environment: 'node',
    globals: true,
  },
  resolve: {
    alias: {
      '@': path.resolve(__dirname, '.'),
    },
  },
});
```

- [ ] **Step 5: Commit**

```bash
git add apps/web/package.json apps/web/vitest.config.ts pnpm-lock.yaml
git commit -m "chore: add blog infrastructure dependencies (MDX, rehype, Fuse.js, OG, Vitest)"
```

---

### Task 2: Blog Types and Utility Library

**Files:**

- Create: `apps/web/lib/blog-types.ts`
- Create: `apps/web/lib/blog.ts`
- Create: `apps/web/__tests__/lib/blog.test.ts`
- Create: `apps/web/content/blog/intercom-vs-zendesk.mdx` (test fixture)

- [ ] **Step 1: Create blog types**

Create `apps/web/lib/blog-types.ts`:

```typescript
export interface BlogPostMeta {
  slug: string;
  title: string;
  description: string;
  date: string;
  updated?: string;
  author: string;
  category: BlogCategory;
  tags: string[];
  coverImage?: string;
  featured?: boolean;
  readTime: number;
}

export type BlogCategory =
  | 'tool-comparison'
  | 'operational-intelligence'
  | 'category-definition'
  | 'changelog';

export const CATEGORY_LABELS: Record<BlogCategory, string> = {
  'tool-comparison': 'Tool Comparisons',
  'operational-intelligence': 'Operational Intelligence',
  'category-definition': 'Category Definitions',
  changelog: 'Changelog',
};

export interface BlogPost extends BlogPostMeta {
  content: string;
  headings: Heading[];
}

export interface Heading {
  text: string;
  slug: string;
  level: 2 | 3;
}
```

- [ ] **Step 2: Create sample MDX post as test fixture**

Create `apps/web/content/blog/intercom-vs-zendesk.mdx`:

```mdx
---
title: 'Intercom vs Zendesk: Which Support Tool Fits a Scaling Startup?'
description: 'A direct comparison of Intercom and Zendesk for startups scaling past 1,000 customers — features, pricing, and what matters when your support data needs to connect.'
date: '2026-03-24'
updated: '2026-03-24'
author: 'Jared Tucker'
category: 'tool-comparison'
tags: ['support-tools', 'intercom', 'zendesk', 'customer-support']
coverImage: 'images/blog/intercom-vs-zendesk.webp'
featured: true
---

Intercom wins on proactive engagement and product tours. Zendesk wins on ticket management at scale. The real question isn't which one — it's whether your support data connects to everything else.

## Quick Comparison

| Feature     | Intercom                             | Zendesk                       |
| ----------- | ------------------------------------ | ----------------------------- |
| Best for    | Product-led growth, in-app messaging | High-volume ticket management |
| Pricing     | From $39/seat/mo                     | From $19/agent/mo             |
| API quality | Excellent                            | Good                          |
| Reporting   | Beautiful, shallow                   | Deep, complex                 |

## When Intercom Makes Sense

Intercom is built for teams that think of support as part of the product experience, not a cost center. Its strength is proactive — product tours, in-app messages, targeted campaigns based on user behavior.

### The Reporting Gap

Intercom's reporting looks great but goes about an inch deep. You can see conversation volume and response times, but correlating support patterns with revenue data requires exporting CSVs and stitching spreadsheets.

## When Zendesk Makes Sense

Zendesk is the boring, reliable choice — and boring is a compliment. It handles high-volume ticket routing, SLA management, and multi-channel support better than anything else in the category.

## The Cross-Domain Connection

The real question isn't which support tool is better in isolation. It's whether your support data connects to your billing data, your product usage data, and your acquisition data. A customer who files 3 support tickets in their first week has a different retention trajectory than one who never contacts support — but neither Intercom nor Zendesk will show you that pattern on their own.

## Frequently Asked Questions

### Is Intercom better than Zendesk for small teams?

For teams under 10, Intercom's all-in-one approach (chat, help center, product tours) means fewer tools to manage. Zendesk requires more configuration but scales better past 50 agents.

### Can you use both Intercom and Zendesk?

Yes — some teams use Intercom for in-app messaging and Zendesk for email ticket management. The challenge is keeping customer context unified across both.

### Which has better integrations?

Both have extensive integration ecosystems. Zendesk has more native integrations (1000+), while Intercom's API is more developer-friendly for custom integrations.
```

- [ ] **Step 3: Write failing tests for blog utilities**

Create `apps/web/__tests__/lib/blog.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';
import {
  getAllPosts,
  getPostBySlug,
  getCategories,
  getRelatedPosts,
  getFeaturedPost,
  calculateReadTime,
} from '@/lib/blog';

describe('calculateReadTime', () => {
  it('returns minutes based on 200 words/minute', () => {
    const words = Array(1000).fill('word').join(' ');
    expect(calculateReadTime(words)).toBe(5);
  });

  it('returns 1 minute minimum for short content', () => {
    expect(calculateReadTime('short')).toBe(1);
  });

  it('rounds up to nearest minute', () => {
    const words = Array(250).fill('word').join(' ');
    expect(calculateReadTime(words)).toBe(2);
  });
});

describe('getAllPosts', () => {
  it('returns posts sorted by date descending', async () => {
    const posts = await getAllPosts();
    expect(posts.length).toBeGreaterThan(0);
    for (let i = 1; i < posts.length; i++) {
      expect(new Date(posts[i - 1].date).getTime()).toBeGreaterThanOrEqual(
        new Date(posts[i].date).getTime(),
      );
    }
  });

  it('includes readTime on every post', async () => {
    const posts = await getAllPosts();
    for (const post of posts) {
      expect(post.readTime).toBeGreaterThanOrEqual(1);
    }
  });
});

describe('getPostBySlug', () => {
  it('returns a post with content and headings for a valid slug', async () => {
    const post = await getPostBySlug('intercom-vs-zendesk');
    expect(post).not.toBeNull();
    expect(post!.title).toContain('Intercom');
    expect(post!.content).toBeTruthy();
    expect(post!.headings.length).toBeGreaterThan(0);
  });

  it('returns null for an invalid slug', async () => {
    const post = await getPostBySlug('does-not-exist');
    expect(post).toBeNull();
  });
});

describe('getCategories', () => {
  it('returns unique categories from published posts', async () => {
    const categories = await getCategories();
    expect(categories.length).toBeGreaterThan(0);
    const unique = new Set(categories.map((c) => c.slug));
    expect(unique.size).toBe(categories.length);
  });
});

describe('getRelatedPosts', () => {
  it('excludes the current post', async () => {
    const related = await getRelatedPosts('intercom-vs-zendesk', ['support-tools'], 3);
    expect(related.every((p) => p.slug !== 'intercom-vs-zendesk')).toBe(true);
  });

  it('returns at most the requested limit', async () => {
    const related = await getRelatedPosts('intercom-vs-zendesk', ['support-tools'], 3);
    expect(related.length).toBeLessThanOrEqual(3);
  });
});

describe('getFeaturedPost', () => {
  it('returns the post with featured: true if one exists', async () => {
    const featured = await getFeaturedPost();
    expect(featured).not.toBeNull();
    expect(featured!.featured).toBe(true);
  });
});
```

- [ ] **Step 4: Run tests to verify they fail**

```bash
cd apps/web && pnpm test
```

Expected: FAIL — `@/lib/blog` module not found.

- [ ] **Step 5: Implement blog utilities**

Create `apps/web/lib/blog.ts`:

```typescript
import fs from 'fs';
import path from 'path';
import { cache } from 'react';
import matter from 'gray-matter';
import readingTime from 'reading-time';
import type { BlogPost, BlogPostMeta, BlogCategory, Heading } from './blog-types';

const CONTENT_DIR = path.join(process.cwd(), 'content', 'blog');

export function calculateReadTime(content: string): number {
  const { minutes } = readingTime(content);
  return Math.max(1, Math.ceil(minutes));
}

function extractHeadings(content: string): Heading[] {
  const headingRegex = /^#{2,3}\s+(.+)$/gm;
  const headings: Heading[] = [];
  let match;

  while ((match = headingRegex.exec(content)) !== null) {
    const level = match[0].startsWith('###') ? 3 : 2;
    const text = match[1].trim();
    const slug = text
      .toLowerCase()
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/(^-|-$)/g, '');
    headings.push({ text, slug, level: level as 2 | 3 });
  }

  return headings;
}

function parseFrontmatter(filePath: string): { meta: BlogPostMeta; content: string } | null {
  try {
    const raw = fs.readFileSync(filePath, 'utf-8');
    const { data, content } = matter(raw);

    const slug = path.basename(filePath, '.mdx');

    const meta: BlogPostMeta = {
      slug,
      title: data.title,
      description: data.description,
      date: data.date,
      updated: data.updated,
      author: data.author,
      category: data.category as BlogCategory,
      tags: data.tags ?? [],
      coverImage: data.coverImage,
      featured: data.featured ?? false,
      readTime: calculateReadTime(content),
    };

    return { meta, content };
  } catch {
    return null;
  }
}

export async function getAllPosts(): Promise<BlogPostMeta[]> {
  if (!fs.existsSync(CONTENT_DIR)) return [];

  const files = fs.readdirSync(CONTENT_DIR).filter((f) => f.endsWith('.mdx'));

  const posts = files
    .map((file) => parseFrontmatter(path.join(CONTENT_DIR, file)))
    .filter((p): p is NonNullable<typeof p> => p !== null)
    .map((p) => p.meta)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

  return posts;
}

export const getPostBySlug = cache(async (slug: string): Promise<BlogPost | null> => {
  const filePath = path.join(CONTENT_DIR, `${slug}.mdx`);
  const parsed = parseFrontmatter(filePath);
  if (!parsed) return null;

  return {
    ...parsed.meta,
    content: parsed.content,
    headings: extractHeadings(parsed.content),
  };
});

export async function getCategories(): Promise<{ slug: BlogCategory; count: number }[]> {
  const posts = await getAllPosts();
  const counts = new Map<BlogCategory, number>();

  for (const post of posts) {
    counts.set(post.category, (counts.get(post.category) ?? 0) + 1);
  }

  return Array.from(counts.entries())
    .map(([slug, count]) => ({ slug, count }))
    .sort((a, b) => b.count - a.count);
}

export async function getRelatedPosts(
  currentSlug: string,
  currentTags: string[],
  limit = 3,
): Promise<BlogPostMeta[]> {
  const posts = await getAllPosts();
  const currentTagSet = new Set(currentTags);

  const scored = posts
    .filter((p) => p.slug !== currentSlug)
    .map((post) => {
      const overlap = post.tags.filter((t) => currentTagSet.has(t)).length;
      return { post, overlap };
    })
    .sort(
      (a, b) =>
        b.overlap - a.overlap || new Date(b.post.date).getTime() - new Date(a.post.date).getTime(),
    );

  return scored.slice(0, limit).map((s) => s.post);
}

export async function getFeaturedPost(): Promise<BlogPostMeta | null> {
  const posts = await getAllPosts();
  return posts.find((p) => p.featured) ?? posts[0] ?? null;
}

export async function getPostsByCategory(category: BlogCategory): Promise<BlogPostMeta[]> {
  const posts = await getAllPosts();
  return posts.filter((p) => p.category === category);
}
```

- [ ] **Step 6: Run tests to verify they pass**

```bash
cd apps/web && pnpm test
```

Expected: All tests PASS.

- [ ] **Step 7: Commit**

```bash
git add apps/web/lib/blog-types.ts apps/web/lib/blog.ts apps/web/__tests__/lib/blog.test.ts apps/web/content/blog/intercom-vs-zendesk.mdx
git commit -m "feat: add blog utility library with types, parsing, and tests"
```

---

### Task 3: Fix robots.ts and Update Sitemap

**Files:**

- Modify: `apps/web/app/robots.ts`
- Modify: `apps/web/app/sitemap.ts`

- [ ] **Step 1: Fix robots.ts to allow AI crawlers**

Replace the contents of `apps/web/app/robots.ts`:

```typescript
import type { MetadataRoute } from 'next';

export default function robots(): MetadataRoute.Robots {
  return {
    rules: [
      {
        userAgent: '*',
        allow: '/',
        disallow: ['/api/'],
      },
    ],
    sitemap: 'https://getcranium.io/sitemap.xml',
  };
}
```

This removes the block on GPTBot, CCBot, Google-Extended, and anthropic-ai. All crawlers (including AI) are now allowed everywhere except `/api/`.

- [ ] **Step 2: Update sitemap to include blog routes**

Replace `apps/web/app/sitemap.ts`:

```typescript
import type { MetadataRoute } from 'next';
import { getAllPosts, getCategories } from '@/lib/blog';

const BASE_URL = 'https://getcranium.io';

export default async function sitemap(): Promise<MetadataRoute.Sitemap> {
  const staticRoutes: MetadataRoute.Sitemap = [
    {
      url: BASE_URL,
      lastModified: new Date(),
      changeFrequency: 'monthly',
      priority: 1,
    },
    {
      url: `${BASE_URL}/privacy`,
      lastModified: new Date(),
      changeFrequency: 'yearly',
      priority: 0.3,
    },
    {
      url: `${BASE_URL}/blog`,
      lastModified: new Date(),
      changeFrequency: 'weekly',
      priority: 0.8,
    },
  ];

  const posts = await getAllPosts();
  const blogRoutes: MetadataRoute.Sitemap = posts.map((post) => ({
    url: `${BASE_URL}/blog/${post.slug}`,
    lastModified: new Date(post.updated ?? post.date),
    changeFrequency: 'weekly',
    priority: 0.7,
  }));

  const categories = await getCategories();
  const categoryRoutes: MetadataRoute.Sitemap = categories.map((cat) => ({
    url: `${BASE_URL}/blog/category/${cat.slug}`,
    lastModified: new Date(),
    changeFrequency: 'weekly',
    priority: 0.6,
  }));

  return [...staticRoutes, ...blogRoutes, ...categoryRoutes];
}
```

- [ ] **Step 3: Commit**

```bash
git add apps/web/app/robots.ts apps/web/app/sitemap.ts
git commit -m "fix: allow AI crawlers in robots.ts, add blog routes to sitemap"
```

---

### Task 4: MDX Rendering Components

**Files:**

- Create: `apps/web/components/feature-modules/blog/mdx/mdx-components.tsx`
- Create: `apps/web/components/feature-modules/blog/mdx/code-block.tsx`
- Create: `apps/web/components/feature-modules/blog/mdx/comparison-table.tsx`

- [ ] **Step 1: Create code block with copy-to-clipboard**

Create `apps/web/components/feature-modules/blog/mdx/code-block.tsx`:

```tsx
'use client';

import { Check, Copy } from 'lucide-react';
import { useState } from 'react';

export function CodeBlock({ children, ...props }: React.HTMLAttributes<HTMLPreElement>) {
  const [copied, setCopied] = useState(false);

  const handleCopy = () => {
    const code = (children as React.ReactElement)?.props?.children ?? '';
    navigator.clipboard.writeText(typeof code === 'string' ? code : '');
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="group relative">
      <pre
        className="overflow-x-auto rounded-lg border border-border bg-card p-4 font-mono text-sm leading-relaxed"
        {...props}
      >
        {children}
      </pre>
      <button
        onClick={handleCopy}
        className="absolute top-3 right-3 rounded-sm border border-border bg-card p-1.5 opacity-0 transition-opacity group-hover:opacity-100 focus:opacity-100"
        aria-label="Copy code"
      >
        {copied ? (
          <Check className="text-success size-3.5" />
        ) : (
          <Copy className="size-3.5 text-muted-foreground" />
        )}
      </button>
    </div>
  );
}
```

- [ ] **Step 2: Create interactive comparison table**

Create `apps/web/components/feature-modules/blog/mdx/comparison-table.tsx`:

```tsx
'use client';

import { cn } from '@/lib/utils';
import { ArrowUpDown } from 'lucide-react';
import { useState } from 'react';

interface Column {
  key: string;
  label: string;
}

interface Row {
  [key: string]: string | boolean;
}

interface ComparisonTableProps {
  columns: Column[];
  rows: Row[];
  highlightColumn?: string;
}

function CellValue({ value }: { value: string | boolean }) {
  if (typeof value === 'boolean') {
    return (
      <span className={cn('font-medium', value ? 'text-success' : 'text-muted-foreground')}>
        {value ? 'Yes' : 'No'}
      </span>
    );
  }
  if (value === 'Partial') {
    return <span className="text-warning font-medium">Partial</span>;
  }
  return <span>{value}</span>;
}

export function ComparisonTable({ columns, rows, highlightColumn }: ComparisonTableProps) {
  const [sortKey, setSortKey] = useState<string | null>(null);
  const [sortDir, setSortDir] = useState<'asc' | 'desc'>('asc');

  const handleSort = (key: string) => {
    if (sortKey === key) {
      setSortDir((d) => (d === 'asc' ? 'desc' : 'asc'));
    } else {
      setSortKey(key);
      setSortDir('asc');
    }
  };

  const sorted = sortKey
    ? [...rows].sort((a, b) => {
        const av = String(a[sortKey] ?? '');
        const bv = String(b[sortKey] ?? '');
        return sortDir === 'asc' ? av.localeCompare(bv) : bv.localeCompare(av);
      })
    : rows;

  return (
    <div className="my-6 overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">
        <thead>
          <tr className="border-b border-border bg-muted/50">
            {columns.map((col) => (
              <th
                key={col.key}
                className={cn(
                  'cursor-pointer px-4 py-3 text-left font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase transition-colors hover:text-foreground',
                  highlightColumn === col.key && 'bg-primary/5 text-foreground',
                )}
                onClick={() => handleSort(col.key)}
              >
                <span className="inline-flex items-center gap-1.5">
                  {col.label}
                  <ArrowUpDown className="size-3" />
                </span>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {sorted.map((row, i) => (
            <tr
              key={String(row[columns[0]?.key] ?? i)}
              className="border-b border-border last:border-0"
            >
              {columns.map((col) => (
                <td
                  key={col.key}
                  className={cn('px-4 py-3', highlightColumn === col.key && 'bg-primary/5')}
                >
                  <CellValue value={row[col.key] ?? ''} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
```

- [ ] **Step 3: Create MDX component overrides**

Create `apps/web/components/feature-modules/blog/mdx/mdx-components.tsx`:

```tsx
import { cn } from '@/lib/utils';
import { Link as LinkIcon } from 'lucide-react';
import type { MDXComponents } from 'mdx/types';
import Link from 'next/link';
import { CodeBlock } from './code-block';
import { ComparisonTable } from './comparison-table';

function HeadingLink({
  id,
  level,
  children,
}: {
  id?: string;
  level: number;
  children: React.ReactNode;
}) {
  const Tag = `h${level}` as keyof JSX.IntrinsicElements;
  const sizes: Record<number, string> = {
    2: 'text-2xl font-semibold tracking-tight mt-12 mb-4',
    3: 'text-lg font-semibold tracking-tight mt-8 mb-3',
  };

  return (
    <Tag id={id} className={cn('group relative scroll-mt-24', sizes[level])}>
      {children}
      {id && (
        <a
          href={`#${id}`}
          className="ml-2 inline-block opacity-0 transition-opacity group-focus-within:opacity-100 group-hover:opacity-100"
          aria-label={`Link to ${typeof children === 'string' ? children : 'section'}`}
        >
          <LinkIcon className="size-4 text-muted-foreground" />
        </a>
      )}
    </Tag>
  );
}

export const mdxComponents: MDXComponents = {
  h2: ({ children, id }) => (
    <HeadingLink id={id} level={2}>
      {children}
    </HeadingLink>
  ),
  h3: ({ children, id }) => (
    <HeadingLink id={id} level={3}>
      {children}
    </HeadingLink>
  ),
  p: ({ children }) => <p className="mb-4 leading-relaxed text-content">{children}</p>,
  a: ({ href, children }) => (
    <Link
      href={href ?? '#'}
      className="font-medium text-foreground underline decoration-border underline-offset-4 transition-colors hover:decoration-foreground"
    >
      {children}
    </Link>
  ),
  ul: ({ children }) => <ul className="mb-4 ml-6 list-disc space-y-1 text-content">{children}</ul>,
  ol: ({ children }) => (
    <ol className="mb-4 ml-6 list-decimal space-y-1 text-content">{children}</ol>
  ),
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  blockquote: ({ children }) => (
    <blockquote className="my-6 border-l-2 border-border pl-6 font-[family-name:var(--font-instrument-serif)] text-xl text-muted-foreground">
      {children}
    </blockquote>
  ),
  pre: CodeBlock,
  table: ({ children }) => (
    <div className="my-6 overflow-x-auto rounded-lg border border-border">
      <table className="w-full text-sm">{children}</table>
    </div>
  ),
  thead: ({ children }) => <thead className="border-b border-border bg-muted/50">{children}</thead>,
  th: ({ children }) => (
    <th className="px-4 py-3 text-left font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
      {children}
    </th>
  ),
  td: ({ children }) => <td className="border-t border-border px-4 py-3">{children}</td>,
  hr: () => <hr className="my-8 border-border" />,
  ComparisonTable,
};
```

- [ ] **Step 4: Commit**

```bash
git add apps/web/components/feature-modules/blog/mdx/
git commit -m "feat: add MDX rendering components (code block, comparison table, heading anchors)"
```

---

### Task 5: Blog UI Components

**Files:**

- Create: `apps/web/components/feature-modules/blog/components/blog-hero.tsx`
- Create: `apps/web/components/feature-modules/blog/components/blog-feed.tsx`
- Create: `apps/web/components/feature-modules/blog/components/category-pills.tsx`
- Create: `apps/web/components/feature-modules/blog/components/blog-search.tsx`
- Create: `apps/web/components/feature-modules/blog/components/breadcrumbs.tsx`
- Create: `apps/web/components/feature-modules/blog/components/table-of-contents.tsx`
- Create: `apps/web/components/feature-modules/blog/components/related-posts.tsx`
- Create: `apps/web/components/feature-modules/blog/components/reading-progress.tsx`
- Create: `apps/web/components/feature-modules/blog/components/featured-posts.tsx`
- Create: `apps/web/hooks/use-reading-progress.ts`

This is the largest task. Each component follows the design system tokens from DESIGN.md. Blog index layout: featured hero with cover image → "Browse by category:" in Instrument Serif + horizontal pill buttons → Hex-style text-only feed (date in Space Mono, title in Geist 600, excerpt, author, tags).

- [ ] **Step 1: Create reading progress hook**

Create `apps/web/hooks/use-reading-progress.ts`:

```typescript
import { useEffect, useState } from 'react';

export function useReadingProgress() {
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    const handleScroll = () => {
      const scrollTop = window.scrollY;
      const docHeight = document.documentElement.scrollHeight - window.innerHeight;
      setProgress(docHeight > 0 ? Math.min((scrollTop / docHeight) * 100, 100) : 0);
    };

    window.addEventListener('scroll', handleScroll, { passive: true });
    return () => window.removeEventListener('scroll', handleScroll);
  }, []);

  return progress;
}
```

- [ ] **Step 2: Create reading progress bar**

Create `apps/web/components/feature-modules/blog/components/reading-progress.tsx`:

```tsx
'use client';

import { useReadingProgress } from '@/hooks/use-reading-progress';

export function ReadingProgress() {
  const progress = useReadingProgress();

  return (
    <div
      className="fixed top-0 left-0 z-50 h-0.5 bg-foreground transition-[width] duration-150 ease-out"
      style={{ width: `${progress}%` }}
      role="progressbar"
      aria-valuenow={Math.round(progress)}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-label="Reading progress"
    />
  );
}
```

- [ ] **Step 3: Create breadcrumbs**

Create `apps/web/components/feature-modules/blog/components/breadcrumbs.tsx`:

```tsx
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
      <ol className="flex items-center gap-1.5 font-mono text-xs tracking-widest text-muted-foreground uppercase">
        <li>
          <Link href="/" className="transition-colors hover:text-foreground">
            Home
          </Link>
        </li>
        <li>
          <ChevronRight className="size-3" />
        </li>
        <li>
          <Link href="/blog" className="transition-colors hover:text-foreground">
            Blog
          </Link>
        </li>
        {category && (
          <>
            <li>
              <ChevronRight className="size-3" />
            </li>
            <li>
              <Link
                href={`/blog/category/${category}`}
                className="transition-colors hover:text-foreground"
              >
                {CATEGORY_LABELS[category]}
              </Link>
            </li>
          </>
        )}
        {postTitle && (
          <>
            <li>
              <ChevronRight className="size-3" />
            </li>
            <li className="max-w-48 truncate text-foreground">{postTitle}</li>
          </>
        )}
      </ol>
    </nav>
  );
}
```

- [ ] **Step 4: Create blog hero (featured post with cover image)**

Create `apps/web/components/feature-modules/blog/components/blog-hero.tsx`:

```tsx
import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import Image from 'next/image';
import Link from 'next/link';

export function BlogHero({ post }: { post: BlogPostMeta }) {
  return (
    <Link href={`/blog/${post.slug}`} className="group block">
      <article className="overflow-hidden rounded-lg border border-border transition-colors hover:border-muted-foreground/30">
        {post.coverImage && (
          <div className="aspect-video overflow-hidden">
            <Image
              src={post.coverImage}
              loader={cdnImageLoader}
              alt={post.title}
              width={1200}
              height={675}
              className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
              priority
            />
          </div>
        )}
        <div className="p-6 lg:p-8">
          <span className="font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
            {CATEGORY_LABELS[post.category]}
          </span>
          <h2 className="mt-3 text-2xl font-bold tracking-tight lg:text-3xl">{post.title}</h2>
          <p className="mt-3 text-sm leading-relaxed text-muted-foreground lg:text-base">
            {post.description}
          </p>
          <div className="mt-4 flex items-center gap-3 font-mono text-xs tracking-widest text-muted-foreground uppercase">
            <span>{post.author}</span>
            <span>&middot;</span>
            <time dateTime={post.date}>
              {new Date(post.date).toLocaleDateString('en-US', {
                month: 'short',
                day: 'numeric',
                year: 'numeric',
              })}
            </time>
            <span>&middot;</span>
            <span>{post.readTime} min read</span>
          </div>
        </div>
      </article>
    </Link>
  );
}
```

- [ ] **Step 5: Create category pills**

Create `apps/web/components/feature-modules/blog/components/category-pills.tsx`:

```tsx
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
      <h2 className="mb-4 font-[family-name:var(--font-instrument-serif)] text-2xl text-muted-foreground">
        Browse by category:
      </h2>
      <div className="flex flex-wrap gap-2" role="tablist">
        <Link
          href="/blog"
          role="tab"
          aria-selected={isAll}
          className={cn(
            'rounded-sm border px-3 py-1.5 font-mono text-xs font-bold tracking-widest uppercase transition-colors',
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
              role="tab"
              aria-selected={isActive}
              className={cn(
                'rounded-sm border px-3 py-1.5 font-mono text-xs font-bold tracking-widest uppercase transition-colors',
                isActive
                  ? 'border-foreground bg-foreground text-background'
                  : 'border-border text-muted-foreground hover:border-muted-foreground hover:text-foreground',
              )}
            >
              {CATEGORY_LABELS[cat.slug]} [{cat.count}]
            </Link>
          );
        })}
      </div>
    </div>
  );
}
```

- [ ] **Step 6: Create blog feed (Hex-style text-only)**

Create `apps/web/components/feature-modules/blog/components/blog-feed.tsx`:

```tsx
import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import Link from 'next/link';

export function BlogFeed({ posts }: { posts: BlogPostMeta[] }) {
  if (posts.length === 0) {
    return (
      <div className="py-16 text-center">
        <p className="text-lg text-muted-foreground">No posts published yet. Check back soon.</p>
        <Link
          href="/"
          className="mt-4 inline-block text-sm text-foreground underline underline-offset-4"
        >
          Back to home
        </Link>
      </div>
    );
  }

  return (
    <div className="divide-y divide-border">
      {posts.map((post) => (
        <article key={post.slug} className="py-6 first:pt-0">
          <Link href={`/blog/${post.slug}`} className="group block">
            <div className="mb-2 flex items-center gap-2 font-mono text-xs tracking-widest text-muted-foreground uppercase">
              <time dateTime={post.date}>
                {new Date(post.date).toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric',
                })}
              </time>
              <span>&middot;</span>
              <span>{post.readTime} min</span>
            </div>
            <h3 className="text-lg font-semibold tracking-tight transition-colors group-hover:text-muted-foreground lg:text-xl">
              {post.title}
            </h3>
            <p className="mt-1.5 line-clamp-2 text-sm leading-relaxed text-muted-foreground">
              {post.description}
            </p>
            <div className="mt-3 flex items-center justify-between">
              <span className="font-mono text-xs tracking-widest text-muted-foreground uppercase">
                {post.author}
              </span>
              <div className="flex gap-2">
                <span className="font-mono text-xs tracking-widest text-muted-foreground uppercase">
                  {CATEGORY_LABELS[post.category]}
                </span>
              </div>
            </div>
          </Link>
        </article>
      ))}
    </div>
  );
}
```

- [ ] **Step 7: Create blog search**

Create `apps/web/components/feature-modules/blog/components/blog-search.tsx`:

```tsx
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
        <Search className="absolute top-1/2 left-3 size-4 -translate-y-1/2 text-muted-foreground" />
        <input
          type="text"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          placeholder="Search posts..."
          aria-label="Search blog posts"
          className="w-full rounded-sm border border-border bg-transparent py-2.5 pr-10 pl-10 text-sm text-foreground placeholder:text-muted-foreground focus:border-muted-foreground focus:outline-none"
        />
        {query && (
          <button
            onClick={() => setQuery('')}
            className="absolute top-1/2 right-3 -translate-y-1/2 text-muted-foreground hover:text-foreground"
            aria-label="Clear search"
          >
            <X className="size-4" />
          </button>
        )}
      </div>
      {query && results.length === 0 ? (
        <div className="py-12 text-center">
          <p className="text-muted-foreground">No posts matching &ldquo;{query}&rdquo;.</p>
          <button
            onClick={() => setQuery('')}
            className="mt-2 text-sm text-foreground underline underline-offset-4"
          >
            Clear search
          </button>
        </div>
      ) : (
        <BlogFeed posts={results} />
      )}
    </div>
  );
}
```

- [ ] **Step 8: Create table of contents**

Create `apps/web/components/feature-modules/blog/components/table-of-contents.tsx`:

```tsx
'use client';

import type { Heading } from '@/lib/blog-types';
import { cn } from '@/lib/utils';
import { ChevronDown } from 'lucide-react';
import { useEffect, useState } from 'react';

interface TableOfContentsProps {
  headings: Heading[];
}

export function TableOfContents({ headings }: TableOfContentsProps) {
  const [activeSlug, setActiveSlug] = useState('');
  const [isOpen, setIsOpen] = useState(false);

  useEffect(() => {
    const observer = new IntersectionObserver(
      (entries) => {
        for (const entry of entries) {
          if (entry.isIntersecting) {
            setActiveSlug(entry.target.id);
          }
        }
      },
      { rootMargin: '-80px 0px -80% 0px' },
    );

    for (const heading of headings) {
      const el = document.getElementById(heading.slug);
      if (el) observer.observe(el);
    }

    return () => observer.disconnect();
  }, [headings]);

  if (headings.length === 0) return null;

  const list = (
    <ul className="space-y-2 text-sm">
      {headings.map((h) => (
        <li key={h.slug} className={h.level === 3 ? 'ml-4' : ''}>
          <a
            href={`#${h.slug}`}
            onClick={() => setIsOpen(false)}
            className={cn(
              'block py-0.5 transition-colors',
              activeSlug === h.slug
                ? 'font-medium text-foreground'
                : 'text-muted-foreground hover:text-foreground',
            )}
          >
            {h.text}
          </a>
        </li>
      ))}
    </ul>
  );

  return (
    <>
      {/* Desktop: sticky sidebar */}
      <nav aria-label="Table of contents" className="hidden lg:block">
        <p className="mb-3 font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
          On this page
        </p>
        {list}
      </nav>

      {/* Mobile: collapsible dropdown */}
      <div className="mb-8 lg:hidden">
        <button
          onClick={() => setIsOpen(!isOpen)}
          className="flex w-full items-center justify-between rounded-sm border border-border px-4 py-2.5 text-sm"
        >
          <span className="font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
            Table of Contents
          </span>
          <ChevronDown
            className={cn(
              'size-4 text-muted-foreground transition-transform',
              isOpen && 'rotate-180',
            )}
          />
        </button>
        {isOpen && <div className="mt-2 rounded-sm border border-border p-4">{list}</div>}
      </div>
    </>
  );
}
```

- [ ] **Step 9: Create related posts**

Create `apps/web/components/feature-modules/blog/components/related-posts.tsx`:

```tsx
import type { BlogPostMeta } from '@/lib/blog-types';
import Link from 'next/link';

export function RelatedPosts({ posts }: { posts: BlogPostMeta[] }) {
  if (posts.length === 0) return null;

  return (
    <section className="mt-16 border-t border-border pt-12">
      <h2 className="mb-6 font-[family-name:var(--font-instrument-serif)] text-2xl text-muted-foreground">
        More from the blog
      </h2>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {posts.map((post) => (
          <Link key={post.slug} href={`/blog/${post.slug}`} className="group block">
            <article>
              <span className="font-mono text-xs tracking-widest text-muted-foreground uppercase">
                {new Date(post.date).toLocaleDateString('en-US', {
                  month: 'short',
                  day: 'numeric',
                  year: 'numeric',
                })}
              </span>
              <h3 className="mt-2 font-semibold tracking-tight transition-colors group-hover:text-muted-foreground">
                {post.title}
              </h3>
              <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">{post.description}</p>
            </article>
          </Link>
        ))}
      </div>
    </section>
  );
}
```

- [ ] **Step 10: Create featured posts (landing page section)**

Create `apps/web/components/feature-modules/blog/components/featured-posts.tsx`:

```tsx
import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import Image from 'next/image';
import Link from 'next/link';

interface FeaturedPostsProps {
  featured: BlogPostMeta;
  recent: BlogPostMeta[];
}

export function FeaturedPosts({ featured, recent }: FeaturedPostsProps) {
  return (
    <section className="relative z-20 px-6 py-20 lg:px-12">
      <h2 className="mb-12 font-[family-name:var(--font-instrument-serif)] text-3xl text-muted-foreground lg:text-4xl">
        Latest from the blog
      </h2>

      <div className="grid gap-8 lg:grid-cols-5">
        {/* Featured post — large with cover image */}
        <Link href={`/blog/${featured.slug}`} className="group lg:col-span-3">
          <article className="overflow-hidden rounded-lg border border-border transition-colors hover:border-muted-foreground/30">
            {featured.coverImage && (
              <div className="aspect-video overflow-hidden">
                <Image
                  src={featured.coverImage}
                  loader={cdnImageLoader}
                  alt={featured.title}
                  width={800}
                  height={450}
                  className="h-full w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
                />
              </div>
            )}
            <div className="p-6">
              <span className="font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
                {CATEGORY_LABELS[featured.category]}
              </span>
              <h3 className="mt-2 text-xl font-bold tracking-tight lg:text-2xl">
                {featured.title}
              </h3>
              <p className="mt-2 line-clamp-2 text-sm text-muted-foreground">
                {featured.description}
              </p>
            </div>
          </article>
        </Link>

        {/* Recent posts — text only */}
        <div className="flex flex-col gap-6 lg:col-span-2">
          {recent.map((post) => (
            <Link key={post.slug} href={`/blog/${post.slug}`} className="group block">
              <article className="rounded-lg border border-border p-5 transition-colors hover:border-muted-foreground/30">
                <span className="font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
                  {CATEGORY_LABELS[post.category]}
                </span>
                <h3 className="mt-2 font-semibold tracking-tight transition-colors group-hover:text-muted-foreground">
                  {post.title}
                </h3>
                <p className="mt-1 line-clamp-2 text-sm text-muted-foreground">
                  {post.description}
                </p>
                <span className="mt-3 inline-block font-mono text-xs tracking-widest text-muted-foreground uppercase">
                  {post.readTime} min read
                </span>
              </article>
            </Link>
          ))}
        </div>
      </div>

      <div className="mt-8 text-center">
        <Link
          href="/blog"
          className="font-mono text-sm tracking-widest text-muted-foreground uppercase transition-colors hover:text-foreground"
        >
          View all posts &rarr;
        </Link>
      </div>
    </section>
  );
}
```

- [ ] **Step 11: Commit all blog components**

```bash
git add apps/web/components/feature-modules/blog/ apps/web/hooks/use-reading-progress.ts
git commit -m "feat: add blog UI components (hero, feed, search, TOC, pills, breadcrumbs, related, featured)"
```

---

### Task 6: Blog Pages

**Files:**

- Create: `apps/web/app/blog/page.tsx`
- Create: `apps/web/app/blog/[slug]/page.tsx`
- Create: `apps/web/app/blog/category/[category]/page.tsx`
- Create: `apps/web/app/blog/rss.xml/route.ts`
- Create: `apps/web/app/api/og/route.tsx`

- [ ] **Step 1: Create blog index page**

Create `apps/web/app/blog/page.tsx`:

```tsx
import { BlogFeed } from '@/components/feature-modules/blog/components/blog-feed';
import { BlogHero } from '@/components/feature-modules/blog/components/blog-hero';
import { BlogSearch } from '@/components/feature-modules/blog/components/blog-search';
import { CategoryPills } from '@/components/feature-modules/blog/components/category-pills';
import { getAllPosts, getCategories, getFeaturedPost } from '@/lib/blog';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Blog',
  description:
    'Tool comparisons, operational intelligence, and cross-domain analytics insights for scaling businesses.',
  openGraph: {
    title: 'Blog | Cranium',
    description:
      'Tool comparisons, operational intelligence, and cross-domain analytics insights for scaling businesses.',
  },
};

export default async function BlogPage() {
  const [posts, categories, featured] = await Promise.all([
    getAllPosts(),
    getCategories(),
    getFeaturedPost(),
  ]);

  const feedPosts = featured ? posts.filter((p) => p.slug !== featured.slug) : posts;

  return (
    <main className="mx-auto max-w-5xl px-6 pt-12 pb-20 lg:px-8">
      {featured && <BlogHero post={featured} />}

      <div className="mt-12">
        <CategoryPills categories={categories} />
      </div>

      <BlogSearch posts={feedPosts} />
    </main>
  );
}
```

- [ ] **Step 2: Create blog post page**

Create `apps/web/app/blog/[slug]/page.tsx`:

```tsx
import { Breadcrumbs } from '@/components/feature-modules/blog/components/breadcrumbs';
import { ReadingProgress } from '@/components/feature-modules/blog/components/reading-progress';
import { RelatedPosts } from '@/components/feature-modules/blog/components/related-posts';
import { TableOfContents } from '@/components/feature-modules/blog/components/table-of-contents';
import { mdxComponents } from '@/components/feature-modules/blog/mdx/mdx-components';
import { CATEGORY_LABELS } from '@/lib/blog-types';
import { getAllPosts, getPostBySlug, getRelatedPosts } from '@/lib/blog';
import type { Metadata } from 'next';
import { MDXRemote } from 'next-mdx-remote/rsc';
import { notFound } from 'next/navigation';
import rehypeAutolinkHeadings from 'rehype-autolink-headings';
import rehypePrettyCode from 'rehype-pretty-code';
import rehypeSlug from 'rehype-slug';

interface Props {
  params: Promise<{ slug: string }>;
}

export async function generateStaticParams() {
  const posts = await getAllPosts();
  return posts.map((post) => ({ slug: post.slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { slug } = await params;
  const post = await getPostBySlug(slug);
  if (!post) return {};

  const ogImageUrl = `/api/og?slug=${slug}`;

  return {
    title: post.title,
    description: post.description,
    openGraph: {
      title: post.title,
      description: post.description,
      type: 'article',
      publishedTime: post.date,
      modifiedTime: post.updated ?? post.date,
      authors: [post.author],
      images: [{ url: ogImageUrl, width: 1200, height: 630 }],
    },
    twitter: {
      card: 'summary_large_image',
      title: post.title,
      description: post.description,
      images: [ogImageUrl],
    },
  };
}

function ArticleJsonLd({ post }: { post: Awaited<ReturnType<typeof getPostBySlug>> }) {
  if (!post) return null;
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: post.title,
    description: post.description,
    datePublished: post.date,
    dateModified: post.updated ?? post.date,
    author: { '@type': 'Person', name: post.author },
    publisher: { '@type': 'Organization', name: 'Cranium', url: 'https://getcranium.io' },
  };
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
    />
  );
}

function BreadcrumbJsonLd({ post }: { post: Awaited<ReturnType<typeof getPostBySlug>> }) {
  if (!post) return null;
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Home', item: 'https://getcranium.io' },
      { '@type': 'ListItem', position: 2, name: 'Blog', item: 'https://getcranium.io/blog' },
      {
        '@type': 'ListItem',
        position: 3,
        name: CATEGORY_LABELS[post.category],
        item: `https://getcranium.io/blog/category/${post.category}`,
      },
      { '@type': 'ListItem', position: 4, name: post.title },
    ],
  };
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }}
    />
  );
}

export default async function BlogPostPage({ params }: Props) {
  const { slug } = await params;
  const post = await getPostBySlug(slug);
  if (!post) notFound();

  const related = await getRelatedPosts(slug, post.tags, 3);

  return (
    <>
      <ReadingProgress />
      <ArticleJsonLd post={post} />
      <BreadcrumbJsonLd post={post} />

      <main className="mx-auto max-w-5xl px-6 pt-12 pb-20 lg:px-8">
        <Breadcrumbs category={post.category} postTitle={post.title} />

        <div className="lg:grid lg:grid-cols-[1fr_200px] lg:gap-12">
          <article className="max-w-prose">
            <header className="mb-12">
              <span className="font-mono text-xs font-bold tracking-widest text-muted-foreground uppercase">
                {CATEGORY_LABELS[post.category]}
              </span>
              <h1 className="mt-3 text-3xl font-bold tracking-tight lg:text-4xl">{post.title}</h1>
              <div className="mt-4 flex items-center gap-3 font-mono text-xs tracking-widest text-muted-foreground uppercase">
                <time dateTime={post.date}>
                  {new Date(post.date).toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </time>
                <span>&middot;</span>
                <span>{post.readTime} min read</span>
                <span>&middot;</span>
                <span>By {post.author}</span>
              </div>
              {post.updated && post.updated !== post.date && (
                <p className="mt-2 text-xs text-muted-foreground">
                  Last updated:{' '}
                  {new Date(post.updated).toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </p>
              )}
            </header>

            <MDXRemote
              source={post.content}
              components={mdxComponents}
              options={{
                mdxOptions: {
                  rehypePlugins: [
                    rehypeSlug,
                    [rehypeAutolinkHeadings, { behavior: 'wrap' }],
                    [rehypePrettyCode, { theme: 'github-dark-default' }],
                  ],
                },
              }}
            />
          </article>

          {/* TOC: renders as mobile dropdown OR desktop sticky sidebar via internal responsive logic */}
          <aside className="order-first lg:order-last">
            <div className="lg:sticky lg:top-24">
              <TableOfContents headings={post.headings} />
            </div>
          </aside>
        </div>

        <RelatedPosts posts={related} />
      </main>
    </>
  );
}
```

**Note:** The `TableOfContents` component handles both mobile (collapsible dropdown) and desktop (sticky sidebar) layouts internally via responsive CSS. It's rendered once in the `<aside>`, positioned via CSS grid `order-first` on mobile, `order-last` on desktop.

- [ ] **Step 3: Create category page**

Create `apps/web/app/blog/category/[category]/page.tsx`:

```tsx
import { BlogFeed } from '@/components/feature-modules/blog/components/blog-feed';
import { CategoryPills } from '@/components/feature-modules/blog/components/category-pills';
import { CATEGORY_LABELS, type BlogCategory } from '@/lib/blog-types';
import { getCategories, getPostsByCategory } from '@/lib/blog';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ category: string }>;
}

export async function generateStaticParams() {
  const categories = await getCategories();
  return categories.map((c) => ({ category: c.slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { category } = await params;
  const label = CATEGORY_LABELS[category as BlogCategory];
  if (!label) return {};
  return {
    title: `${label} — Blog`,
    description: `Browse ${label.toLowerCase()} articles on the Cranium blog.`,
  };
}

export default async function CategoryPage({ params }: Props) {
  const { category } = await params;
  if (!CATEGORY_LABELS[category as BlogCategory]) notFound();

  const [posts, categories] = await Promise.all([
    getPostsByCategory(category as BlogCategory),
    getCategories(),
  ]);

  if (posts.length === 0) notFound();

  return (
    <main className="mx-auto max-w-5xl px-6 pt-12 pb-20 lg:px-8">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">
        {CATEGORY_LABELS[category as BlogCategory]}
      </h1>
      <CategoryPills categories={categories} />
      <BlogFeed posts={posts} />
    </main>
  );
}
```

- [ ] **Step 4: Create RSS feed route**

Create `apps/web/app/blog/rss.xml/route.ts`:

```typescript
import { getAllPosts } from '@/lib/blog';

const BASE_URL = 'https://getcranium.io';

export async function GET() {
  const posts = await getAllPosts();
  const recent = posts.slice(0, 20);

  const items = recent
    .map(
      (post) => `
    <item>
      <title><![CDATA[${post.title}]]></title>
      <link>${BASE_URL}/blog/${post.slug}</link>
      <guid isPermaLink="true">${BASE_URL}/blog/${post.slug}</guid>
      <description><![CDATA[${post.description}]]></description>
      <pubDate>${new Date(post.date).toUTCString()}</pubDate>
      <author>jared@cranium.software (${post.author})</author>
    </item>`,
    )
    .join('');

  const feed = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>Cranium Blog</title>
    <link>${BASE_URL}/blog</link>
    <description>Tool comparisons, operational intelligence, and cross-domain analytics insights.</description>
    <language>en-us</language>
    <lastBuildDate>${new Date().toUTCString()}</lastBuildDate>
    <atom:link href="${BASE_URL}/blog/rss.xml" rel="self" type="application/rss+xml"/>
    ${items}
  </channel>
</rss>`;

  return new Response(feed.trim(), {
    headers: {
      'Content-Type': 'application/rss+xml; charset=utf-8',
      'Cache-Control': 'public, max-age=3600, s-maxage=3600',
    },
  });
}
```

- [ ] **Step 5: Create dynamic OG image route**

Create `apps/web/app/api/og/route.tsx`:

```tsx
import { getPostBySlug } from '@/lib/blog';
import { CATEGORY_LABELS } from '@/lib/blog-types';
import { ImageResponse } from 'next/og';
import type { NextRequest } from 'next/server';

export async function GET(request: NextRequest) {
  const slug = request.nextUrl.searchParams.get('slug');

  if (!slug) {
    return new ImageResponse(
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#1a1a1a',
          color: '#ffffff',
          fontSize: 48,
          fontWeight: 700,
        }}
      >
        Cranium Blog
      </div>,
      { width: 1200, height: 630 },
    );
  }

  const post = await getPostBySlug(slug);
  if (!post) {
    return new Response('Not found', { status: 404 });
  }

  return new ImageResponse(
    <div
      style={{
        width: '100%',
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        justifyContent: 'space-between',
        padding: '60px 80px',
        backgroundColor: '#0a0a0a',
        color: '#ffffff',
      }}
    >
      <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
        <span
          style={{
            fontSize: 14,
            fontWeight: 700,
            textTransform: 'uppercase',
            letterSpacing: '0.1em',
            color: '#888',
          }}
        >
          {CATEGORY_LABELS[post.category]}
        </span>
        <span
          style={{
            fontSize: 48,
            fontWeight: 700,
            lineHeight: 1.1,
            letterSpacing: '-0.02em',
            maxWidth: '900px',
          }}
        >
          {post.title}
        </span>
      </div>
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'flex-end',
          fontSize: 16,
          color: '#888',
        }}
      >
        <span>
          {post.author} &middot; {post.readTime} min read
        </span>
        <span style={{ fontSize: 20, fontWeight: 700, color: '#fff' }}>Cranium</span>
      </div>
    </div>,
    {
      width: 1200,
      height: 630,
      headers: {
        'Cache-Control': 'public, max-age=604800, s-maxage=604800',
      },
    },
  );
}
```

- [ ] **Step 6: Commit all pages and routes**

```bash
git add apps/web/app/blog/ apps/web/app/api/og/
git commit -m "feat: add blog pages (index, post, category), RSS feed, and OG image generation"
```

---

### Task 7: Landing Page Integration

**Files:**

- Modify: `apps/web/app/page.tsx`

- [ ] **Step 1: Convert landing page to server component and add featured posts**

Replace `apps/web/app/page.tsx`:

```tsx
import { DailyActions } from '@/components/feature-modules/actions/components/daily-actions';
import { FeaturedPosts } from '@/components/feature-modules/blog/components/featured-posts';
import { ChurnRetrospective } from '@/components/feature-modules/churn-retrospective/churn-retro';
import { DashboardShowcase } from '@/components/feature-modules/hero/components/dashboard/dashboard-showcase';
import { Hero } from '@/components/feature-modules/hero/components/hero';
import { getAllPosts, getFeaturedPost } from '@/lib/blog';
import dynamic from 'next/dynamic';

const CrossDomainIntelligence = dynamic(() =>
  import('@/components/feature-modules/cross-domain-intelligence/cross-domain-section').then(
    (m) => m.CrossDomainIntelligence,
  ),
);
const TimeSaved = dynamic(() =>
  import('@/components/feature-modules/time-saved/components/time-saved').then((m) => m.TimeSaved),
);
const Faq = dynamic(() =>
  import('@/components/feature-modules/faq/components/faq').then((m) => m.Faq),
);
const Waitlist = dynamic(() =>
  import('@/components/feature-modules/waitlist/components/waitlist').then((m) => m.Waitlist),
);

export default async function Home() {
  const [featured, posts] = await Promise.all([getFeaturedPost(), getAllPosts()]);

  const recent = posts.filter((p) => p.slug !== featured?.slug).slice(0, 2);
  const showBlog = featured !== null;

  return (
    <main className="min-h-screen overflow-x-hidden">
      <Hero />
      <DashboardShowcase />
      <CrossDomainIntelligence />
      <TimeSaved />
      <ChurnRetrospective />
      <DailyActions />
      {showBlog && <FeaturedPosts featured={featured} recent={recent} />}
      <Faq />
      <Waitlist />
    </main>
  );
}
```

Key changes:

- Removed `'use client'` directive — now a server component
- Added `async` to enable server-side blog data fetching
- Added `FeaturedPosts` section between `DailyActions` and `Faq`
- Featured posts section only renders when blog posts exist

- [ ] **Step 2: Verify the page still builds**

```bash
cd apps/web && pnpm build
```

If this fails due to client components expecting a client-side page: check each imported component. Components using browser-only APIs (e.g., `@paper-design/shaders-react`, Framer Motion hooks, window/document access) need either their own `'use client'` directive or `{ ssr: false }` in the `dynamic()` call. The `Hero`, `DashboardShowcase`, `DailyActions`, and `ChurnRetrospective` are imported directly — verify each has `'use client'` at the top of its file. If any don't, add `{ ssr: false }` to their dynamic import.

- [ ] **Step 3: Commit**

```bash
git add apps/web/app/page.tsx
git commit -m "feat: convert landing page to server component, add featured blog posts section"
```

---

### Task 8: Second Sample Post + Verify Build

**Files:**

- Create: `apps/web/content/blog/tracking-churn-across-tools.mdx`

- [ ] **Step 1: Create second sample post to test feed with multiple posts**

Create `apps/web/content/blog/tracking-churn-across-tools.mdx`:

```mdx
---
title: 'How to Track Churn When Your Data Lives in 6 Different Tools'
description: 'Most DTC operators can tell you their churn rate. Almost none can tell you why customers leave — because the signals are scattered across Stripe, Gorgias, Shopify, and 3 other tools.'
date: '2026-03-20'
updated: '2026-03-20'
author: 'Jared Tucker'
category: 'operational-intelligence'
tags: ['churn', 'analytics', 'DTC', 'cross-domain']
---

Most DTC operators can tell you their churn rate. Almost none can tell you _why_ customers leave. The signals are there — a support ticket here, a failed payment there, a product return two weeks ago — but they're scattered across 6 different tools that don't talk to each other.

## The 15-Minute Customer View Problem

Try to answer this question: "Why did Sarah Chen cancel her subscription last week?"

To find out, you'd need to check:

- **Stripe** — payment history, failed charges, refunds
- **Gorgias** — support tickets, satisfaction scores
- **Shopify** — order history, returns
- **Klaviyo** — email engagement, campaign responses
- **Your product** — usage patterns, feature adoption
- **Your spreadsheet** — the one where you manually track high-value accounts

That's 6 browser tabs, 15 minutes of cross-referencing, and you still might miss the pattern.

## What a Churn Signal Actually Looks Like

Churn doesn't happen in one tool. It happens across all of them:

1. Customer opens a support ticket about delivery delays (Gorgias)
2. Ticket is resolved but rated "unsatisfied" (Gorgias)
3. Customer doesn't open the next 3 email campaigns (Klaviyo)
4. Payment fails on renewal, retried 3 times (Stripe)
5. Customer doesn't update payment method (Stripe)
6. Subscription cancelled automatically (Stripe)

Each event lives in a different system. No single tool sees the full picture.

## The Manual Approach

Here's how most operators handle this today:

### Weekly CSV Export Ritual

1. Export churned customers from Stripe (last 7 days)
2. Cross-reference each email against Gorgias tickets
3. Check Shopify for returns or complaints
4. Look up Klaviyo engagement scores
5. Record findings in a spreadsheet
6. Repeat next week

This takes 4-8 hours per week for a team managing 1,000+ customers. And by the time you've identified the pattern, 50 more customers have already started down the same path.

## Frequently Asked Questions

### How do you calculate churn rate across multiple tools?

Start with billing data (Stripe/Chargebee) as your source of truth for active vs. churned. Then enrich each churned customer with signals from support, product, and marketing tools to understand the _why_ behind the number.

### What's a good churn rate for DTC e-commerce?

Monthly churn rates for subscription DTC brands typically range from 5-10%. But the aggregate number hides everything — churn by acquisition channel, by product, by season. The operators who reduce churn are the ones who can segment it.

### Can you predict churn before it happens?

With connected data, yes. The pattern of "support ticket + low email engagement + failed payment" is visible days or weeks before cancellation. But only if your tools talk to each other.
```

- [ ] **Step 2: Run full test suite**

```bash
cd apps/web && pnpm test
```

Expected: All tests pass with both sample posts.

- [ ] **Step 3: Run build to verify everything compiles**

```bash
cd apps/web && pnpm build
```

Expected: Build succeeds. Blog pages are statically generated.

- [ ] **Step 4: Commit**

```bash
git add apps/web/content/blog/tracking-churn-across-tools.mdx
git commit -m "feat: add second sample blog post, verify build passes"
```

---

## Summary

8 tasks total:

1. **Dependencies** — install MDX, rehype, Fuse.js, OG, Vitest
2. **Blog library** — types, utilities, tests, first sample post
3. **SEO fixes** — robots.ts, sitemap.ts
4. **MDX components** — code block, comparison table, heading anchors
5. **Blog UI** — hero, feed, search, pills, TOC, breadcrumbs, related, featured, progress bar
6. **Blog pages** — index, post, category, RSS, OG image
7. **Landing page** — server component conversion, featured posts section
8. **Second post + build verification**

Tasks 1-3 can run in parallel. Task 4 depends on 1. Task 5 depends on 1 and 2. Task 6 depends on 2, 4, and 5. Task 7 depends on 2 and 5. Task 8 depends on all.
