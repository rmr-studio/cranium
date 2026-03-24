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
              activeSlug === h.slug ? 'font-medium text-foreground' : 'text-muted-foreground hover:text-foreground',
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
        <p className="mb-3 font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground">On this page</p>
        {list}
      </nav>

      {/* Mobile: collapsible dropdown */}
      <div className="mb-8 lg:hidden">
        <button
          onClick={() => setIsOpen(!isOpen)}
          aria-expanded={isOpen}
          className="flex w-full items-center justify-between rounded-sm border border-border px-4 py-2.5 text-sm"
        >
          <span className="font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground">
            Table of Contents
          </span>
          <ChevronDown className={cn('size-4 text-muted-foreground transition-transform', isOpen && 'rotate-180')} />
        </button>
        {isOpen && <div className="mt-2 rounded-sm border border-border p-4">{list}</div>}
      </div>
    </>
  );
}
