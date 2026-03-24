import { cn } from '@/lib/utils';
import { Link as LinkIcon } from 'lucide-react';
import type { MDXComponents } from 'mdx/types';
import Link from 'next/link';
import { CodeBlock } from './code-block';
import { ComparisonTable } from './comparison-table';

function HeadingLink({ id, level, children }: { id?: string; level: number; children: React.ReactNode }) {
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
          className="ml-2 inline-block opacity-0 transition-opacity group-hover:opacity-100 group-focus-within:opacity-100"
          aria-label={`Link to ${typeof children === 'string' ? children : 'section'}`}
        >
          <LinkIcon className="size-4 text-muted-foreground" />
        </a>
      )}
    </Tag>
  );
}

export const mdxComponents: MDXComponents = {
  h2: ({ children, id }) => <HeadingLink id={id} level={2}>{children}</HeadingLink>,
  h3: ({ children, id }) => <HeadingLink id={id} level={3}>{children}</HeadingLink>,
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
  ol: ({ children }) => <ol className="mb-4 ml-6 list-decimal space-y-1 text-content">{children}</ol>,
  li: ({ children }) => <li className="leading-relaxed">{children}</li>,
  blockquote: ({ children }) => (
    <blockquote className="my-6 border-l-2 border-border pl-6 font-[family-name:var(--font-instrument-serif)] text-xl italic text-muted-foreground">
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
    <th className="px-4 py-3 text-left font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground">
      {children}
    </th>
  ),
  td: ({ children }) => <td className="border-t border-border px-4 py-3">{children}</td>,
  hr: () => <hr className="my-8 border-border" />,
  ComparisonTable,
};
