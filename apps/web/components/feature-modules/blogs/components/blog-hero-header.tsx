import { BGPattern } from '@/components/ui/background/grids';
import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import { getCdnUrl } from '@/lib/cdn-image-loader';
import { cn } from '@/lib/utils';
import Image from 'next/image';
import Link from 'next/link';

type BlogHeroVariant = 'overview' | 'category' | 'post';

interface BlogHeroHeaderProps {
  post: BlogPostMeta;
  variant?: BlogHeroVariant;
  /** Content rendered above the featured post (e.g., category heading, breadcrumbs) */
  topSlot?: React.ReactNode;
  /** Extra metadata elements inserted into the meta line (e.g., tags on post pages) */
  metaSlot?: React.ReactNode;
}

/* ── Shared  section wrapper with dot pattern ── */
function HeroWrapper({ children, className }: { children: React.ReactNode; className?: string }) {
  return (
    <section className={cn('relative mt-18 bg-foreground/90 text-background', className)}>
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
      {children}
    </section>
  );
}

/* ── Featured post metadata (category label, title, description, author/date/readtime) ── */
function FeaturedPostMeta({
  post,
  inverted = true,
  large = false,
}: {
  post: BlogPostMeta;
  inverted?: boolean;
  large?: boolean;
}) {
  const textMuted = inverted ? 'text-background/50' : 'text-muted-foreground';
  const textBody = inverted ? 'text-background/60' : 'text-muted-foreground';

  return (
    <div>
      <span className={cn('font-mono text-xs font-bold tracking-widest uppercase', textMuted)}>
        {CATEGORY_LABELS[post.category]}
      </span>
      <h2
        className={cn(
          'mt-3 font-serif tracking-tight',
          large ? 'text-3xl lg:text-5xl' : 'text-3xl lg:text-4xl',
        )}
      >
        {post.title}
      </h2>
      <p className={cn('mt-3 leading-snug', large && 'mt-4 text-lg', textBody)}>
        {post.description}
      </p>
      <div
        className={cn(
          'mt-5 flex items-center gap-3 font-mono text-xs tracking-widest uppercase',
          textMuted,
        )}
      >
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
  );
}

/* ── Cover image with hover scale ── */
function CoverImage({ post, className }: { post: BlogPostMeta; className?: string }) {
  if (!post.coverImage) return null;
  return (
    <div className={cn('overflow-hidden rounded-lg', className)}>
      <Image
        src={getCdnUrl(post.coverImage)}
        alt={post.title}
        width={1260}
        height={720}
        className="w-full object-cover transition-transform duration-300 group-hover:scale-[1.02]"
        unoptimized
        priority
      />
    </div>
  );
}

/* ── Main exported component ── */
export function BlogHeroHeader({
  post,
  variant = 'overview',
  topSlot,
  metaSlot,
}: BlogHeroHeaderProps) {
  const hasCover = !!post.coverImage;

  /* ── Overview: page title in  section, featured card overlaps boundary ── */
  if (variant === 'overview') {
    return (
      <>
        <HeroWrapper>
          <div className="mx-auto max-w-5xl px-6 pt-16 lg:px-8">{topSlot}</div>
          <div className="pb-48 sm:pb-56 lg:pb-72" />
        </HeroWrapper>
        <div className="relative z-10 mx-auto -mt-48 max-w-5xl px-6 sm:-mt-56 lg:-mt-72 lg:px-8">
          <Link
            href={`/resources/blog/${post.slug}`}
            className="group :shadow-none block overflow-hidden rounded-lg border border-border bg-card/30 shadow-lg shadow-foreground/10"
          >
            <CoverImage post={post} className="rounded-none" />
            <div className="p-6 lg:p-8">
              <FeaturedPostMeta post={post} inverted={false} />
            </div>
          </Link>
        </div>
      </>
    );
  }

  /* ── Category: label at top, 2-col grid with cover + post info ── */
  if (variant === 'category') {
    return (
      <HeroWrapper>
        <div className="mx-auto max-w-5xl px-6 pt-16 pb-20 lg:px-8">
          {topSlot}
          <Link href={`/resources/blog/${post.slug}`} className="group block">
            {hasCover ? (
              <div className="grid items-end gap-8 lg:grid-cols-2">
                <CoverImage post={post} className="z-50" />
                <div className="pb-2">
                  <FeaturedPostMeta post={post} />
                </div>
              </div>
            ) : (
              <FeaturedPostMeta post={post} large />
            )}
          </Link>
        </div>
      </HeroWrapper>
    );
  }

  /* ── Post: breadcrumbs + header, cover overlaps /light boundary ── */
  return (
    <>
      <HeroWrapper>
        <div className="mx-auto max-w-5xl px-6 pt-10 pb-12 lg:px-8">
          {topSlot}
          <header className="mt-4 max-w-3xl">
            <span className="font-mono text-xs font-bold tracking-widest text-background/50 uppercase">
              {CATEGORY_LABELS[post.category]}
            </span>
            <h1 className="mt-4 font-serif text-4xl tracking-tight lg:text-5xl">{post.title}</h1>
            <p className="mt-4 text-lg leading-snug text-background/60">{post.description}</p>
            <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-background/10 pt-5">
              <span className="font-mono text-xs tracking-widest text-background/50 uppercase">
                {post.author}
              </span>
              {metaSlot}
              <time
                dateTime={post.date}
                className="font-mono text-xs tracking-widest text-background/50 uppercase"
              >
                {new Date(post.date).toLocaleDateString('en-US', {
                  month: 'long',
                  day: 'numeric',
                  year: 'numeric',
                })}
              </time>
            </div>
          </header>
        </div>
        {hasCover && <div className="pb-24 sm:pb-32 lg:pb-64" />}
      </HeroWrapper>
      {hasCover && (
        <div className="relative z-10 mx-auto -mt-24 max-w-5xl px-6 sm:-mt-32 lg:-mt-64 lg:px-8">
          <div className=":shadow-none overflow-hidden rounded-lg shadow-lg shadow-foreground/40">
            <Image
              src={getCdnUrl(post.coverImage!)}
              alt={`Cover image for ${post.title}`}
              width={1260}
              height={720}
              className="w-full object-cover"
              priority
              unoptimized
            />
          </div>
        </div>
      )}
    </>
  );
}
