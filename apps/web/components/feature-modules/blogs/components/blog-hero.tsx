'use client';

import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import Image from 'next/image';
import Link from 'next/link';

export function BlogHero({ post }: { post: BlogPostMeta }) {
  return (
    <Link href={`/resources/blog/${post.slug}`} className="group block">
      <article className="overflow-hidden rounded-lg border border-border transition-colors hover:border-muted-foreground/30">
        {post.coverImage && (
          <div className="aspect-video overflow-hidden">
            <Image
              src={post.coverImage}
              loader={cdnImageLoader}
              alt={post.title}
              width={1200}
              height={675}
              className="h-full w-full object-cover transition-all duration-300 group-hover:scale-[1.02]"
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
