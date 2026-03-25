import { BGPattern } from '@/components/ui/background/grids';
import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { getCdnUrl } from '@/lib/cdn-image-loader';
import Image from 'next/image';
import Link from 'next/link';

interface CategoryFeaturedProps {
  post: BlogPostMeta;
  categoryLabel: string;
}

export function CategoryFeatured({ post, categoryLabel }: CategoryFeaturedProps) {
  const hasCover = !!post.coverImage;

  return (
    <section className="paper-lite relative bg-foreground/90 text-background">
      <BGPattern
        variant="dots"
        size={12}
        fill="color-mix(in srgb, var(--background) 40%, transparent)"
        mask="none"
        className="z-20"
        style={{
          maskImage:
            'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
          maskComposite: 'intersect',
          WebkitMaskImage:
            'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
          WebkitMaskComposite: 'source-in' as string,
        }}
      />
      <div className="mx-auto max-w-5xl px-6 pt-16 pb-20 lg:px-8">
        <h1 className="mb-14 font-mono text-xs font-bold tracking-widest uppercase">
          {categoryLabel}
        </h1>

        <Link href={`/resources/blog/${post.slug}`} className="group block">
          {hasCover ? (
            <div className="grid items-end gap-8 lg:grid-cols-2">
              {/* Cover image */}
              <div className="z-50 overflow-hidden rounded-lg">
                <Image
                  src={getCdnUrl(post.coverImage!)}
                  alt={post.title}
                  width={1260}
                  height={720}
                  className="w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
                  unoptimized
                />
              </div>

              {/* Text */}
              <div className="pb-2">
                <span className="font-mono text-xs tracking-widest text-background/50 uppercase">
                  {CATEGORY_LABELS[post.category]}
                </span>
                <h2 className="mt-3 font-[family-name:var(--font-instrument-serif)] text-3xl tracking-tight lg:text-4xl">
                  {post.title}
                </h2>
                <p className="mt-3 leading-snug text-background/60">{post.description}</p>
                <div className="mt-5 flex items-center gap-3 font-mono text-xs tracking-widest text-background/50 uppercase">
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
            </div>
          ) : (
            <div className="max-w-3xl">
              <span className="font-mono text-xs tracking-widest text-background/50 uppercase">
                {CATEGORY_LABELS[post.category]}
              </span>
              <h2 className="mt-3 font-[family-name:var(--font-instrument-serif)] text-3xl tracking-tight lg:text-5xl">
                {post.title}
              </h2>
              <p className="mt-4 text-lg leading-snug text-background/60">{post.description}</p>
              <div className="mt-6 flex items-center gap-3 font-mono text-xs tracking-widest text-background/50 uppercase">
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
          )}
        </Link>
      </div>
    </section>
  );
}
