'use client';

import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import Link from 'next/link';

interface FeaturedPostsProps {
  featured: BlogPostMeta | null;
  recent: BlogPostMeta[];
}

export function FeaturedPosts({ featured, recent }: FeaturedPostsProps) {
  if (!featured) return null;

  return (
    <section
      id="latest-from-the-blog"
      className="relative z-20 mx-auto border-x border-x-content/25 px-0! pb-20 2xl:max-w-[min(90dvw,var(--breakpoint-3xl))]"
    >
      {/* Heading */}
      <div className="mb-14 px-8 pt-16 sm:px-12 md:mb-20 md:pt-24 lg:pt-20">
        <h2 className="font-bit text-2xl leading-none sm:text-4xl md:text-5xl lg:text-6xl">
          Latest from the blog.
        </h2>
      </div>

      {/* Desktop grid */}
      <div className="hidden md:block">
        <div className="overflow-hidden border-y border-y-content/50">
          <div className="grid grid-cols-5">
            {/* Featured post — spans 3 cols, cover image + meta */}
            <Link
              href={`/resources/blog/${featured.slug}`}
              className="group col-span-3 flex flex-col"
            >
              {featured.coverImage && (
                <div className="relative aspect-[16/10] overflow-hidden border-b border-content/50 bg-muted">
                  <Image
                    src={featured.coverImage}
                    loader={cdnImageLoader}
                    alt={featured.title}
                    fill
                    sizes="(min-width: 1024px) 60vw, 100vw"
                    className="object-cover transition-transform duration-500 group-hover:scale-[1.02]"
                  />
                </div>
              )}
              <div className="flex flex-1 flex-col p-7 lg:p-8">
                <p className="mb-2 font-display text-xs tracking-wide text-content/70 uppercase">
                  {CATEGORY_LABELS[featured.category]}
                </p>
                <h3 className="mb-3 font-bit text-xl font-medium tracking-tight lg:text-2xl">
                  {featured.title}
                </h3>
                <p className="font-display text-sm leading-[1.2] tracking-tighter text-content/70">
                  {featured.description}
                </p>
                <p className="mt-4 font-display text-xs tracking-wide text-content/70 uppercase">
                  {featured.readTime} min read
                </p>
              </div>
            </Link>

            {/* Recent posts — col-span-2, stacked rows */}
            <div className="col-span-2 flex flex-col border-l border-content/50">
              {recent.map((post, i) => (
                <Link
                  key={post.slug}
                  href={`/resources/blog/${post.slug}`}
                  className={cn(
                    'group flex flex-1 flex-col justify-between p-7 transition-colors hover:bg-content/5 lg:p-8',
                    i > 0 && 'border-t border-content/50',
                  )}
                >
                  <div>
                    <p className="mb-2 font-display text-xs tracking-wide text-content/70 uppercase">
                      {CATEGORY_LABELS[post.category]}
                    </p>
                    <h3 className="mb-2 font-bit text-base font-medium tracking-tight lg:text-lg">
                      {post.title}
                    </h3>
                    <p className="line-clamp-2 font-display text-sm leading-[1.2] tracking-tighter text-content/70">
                      {post.description}
                    </p>
                  </div>
                  <p className="mt-4 font-display text-xs tracking-wide text-content/70 uppercase">
                    {post.readTime} min read
                  </p>
                </Link>
              ))}
            </div>
          </div>
        </div>
      </div>

      {/* Mobile layout */}
      <div className="flex flex-col gap-4 px-8 sm:px-12 md:hidden">
        <Link
          href={`/resources/blog/${featured.slug}`}
          className="group block border-l-2 border-content/50 py-4 pr-3 pl-5"
        >
          {featured.coverImage && (
            <div className="relative mb-4 aspect-[16/10] overflow-hidden bg-muted">
              <Image
                src={featured.coverImage}
                loader={cdnImageLoader}
                alt={featured.title}
                fill
                sizes="100vw"
                className="object-cover"
              />
            </div>
          )}
          <p className="mb-2 font-display text-xs tracking-wide text-content/70 uppercase">
            {CATEGORY_LABELS[featured.category]}
          </p>
          <p className="font-bit text-lg font-medium tracking-tight">{featured.title}</p>
          <p className="mt-1.5 font-display text-sm leading-[1.2] tracking-tighter text-content/90">
            {featured.description}
          </p>
        </Link>
        {recent.map((post) => (
          <Link
            key={post.slug}
            href={`/resources/blog/${post.slug}`}
            className="group block border-l-2 border-content/50 py-4 pr-3 pl-5"
          >
            <p className="mb-2 font-display text-xs tracking-wide text-content/70 uppercase">
              {CATEGORY_LABELS[post.category]}
            </p>
            <p className="font-bit text-base font-medium tracking-tight">{post.title}</p>
            <p className="mt-1.5 font-display text-sm leading-[1.2] tracking-tighter text-content/90">
              {post.description}
            </p>
            <p className="mt-3 font-display text-xs tracking-wide text-content/70 uppercase">
              {post.readTime} min read
            </p>
          </Link>
        ))}
      </div>

      {/* View all posts */}
      <div className="mt-8 px-8 text-right sm:px-12">
        <Link
          href="/resources/blog"
          className="font-display text-xs tracking-wide text-content/70 uppercase transition-colors hover:text-foreground"
        >
          View all posts &rarr;
        </Link>
      </div>
    </section>
  );
}
