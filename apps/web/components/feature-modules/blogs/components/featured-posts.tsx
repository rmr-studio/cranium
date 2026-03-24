'use client';

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
      <h2 className="mb-12 font-[family-name:var(--font-instrument-serif)] text-3xl italic text-muted-foreground lg:text-4xl">
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
              <span className="font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground">
                {CATEGORY_LABELS[featured.category]}
              </span>
              <h3 className="mt-2 text-xl font-bold tracking-tight lg:text-2xl">{featured.title}</h3>
              <p className="mt-2 text-sm text-muted-foreground line-clamp-2">{featured.description}</p>
            </div>
          </article>
        </Link>

        {/* Recent posts — text only */}
        <div className="flex flex-col gap-6 lg:col-span-2">
          {recent.map((post) => (
            <Link key={post.slug} href={`/blog/${post.slug}`} className="group block">
              <article className="rounded-lg border border-border p-5 transition-colors hover:border-muted-foreground/30">
                <span className="font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground">
                  {CATEGORY_LABELS[post.category]}
                </span>
                <h3 className="mt-2 font-semibold tracking-tight transition-colors group-hover:text-muted-foreground">
                  {post.title}
                </h3>
                <p className="mt-1 text-sm text-muted-foreground line-clamp-2">{post.description}</p>
                <span className="mt-3 inline-block font-mono text-xs uppercase tracking-widest text-muted-foreground">
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
          className="font-mono text-sm uppercase tracking-widest text-muted-foreground transition-colors hover:text-foreground"
        >
          View all posts &rarr;
        </Link>
      </div>
    </section>
  );
}
