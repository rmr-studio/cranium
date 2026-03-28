'use client';

import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { cdnImageLoader } from '@/lib/cdn-image-loader';
import Image from 'next/image';
import Link from 'next/link';

interface FeaturedPostsProps {
  featured: BlogPostMeta | null;
  recent: BlogPostMeta[];
}

export function FeaturedPosts({ featured, recent }: FeaturedPostsProps) {
  if (!featured) return null;

  return (
    <section className="relative z-20 px-6 py-20 lg:px-12">
      <h2 className="mb-12 font-[family-name:var(--font-instrument-serif)] text-3xl text-muted-foreground italic lg:text-4xl">
        Latest from the blog
      </h2>

      <div className="grid gap-8 lg:grid-cols-5">
        {/* Featured post — large with cover image */}
        <Link href={`/resources/blog/${featured.slug}`} className="group lg:col-span-3">
          <article className="overflow-hidden rounded-sm border border-foreground/30 bg-card/20 shadow-lg transition-colors hover:border-muted-foreground/30">
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
            <Link key={post.slug} href={`/resources/blog/${post.slug}`} className="group block">
              <article className="rounded-sm border border-foreground/10 bg-card/20 p-5 shadow transition-colors hover:border-muted-foreground/30">
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

      <div className="mt-8 text-center lg:text-right">
        <Link
          href="/resources/blog"
          className="font-mono text-sm tracking-widest text-muted-foreground uppercase transition-colors hover:text-foreground"
        >
          View all posts &rarr;
        </Link>
      </div>
    </section>
  );
}
