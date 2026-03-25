import { Breadcrumbs } from '@/components/feature-modules/blogs/components/breadcrumbs';
import { ReadingProgress } from '@/components/feature-modules/blogs/components/reading-progress';
import { RelatedPosts } from '@/components/feature-modules/blogs/components/related-posts';
import { TableOfContents } from '@/components/feature-modules/blogs/components/table-of-contents';
import { mdxComponents } from '@/components/feature-modules/blogs/mdx/mdx-components';
import { BGPattern } from '@/components/ui/background/grids';
import { getAllPosts, getPostBySlug, getRelatedPosts } from '@/lib/blog';
import { CATEGORY_LABELS } from '@/lib/blog-types';
import { getCdnUrl } from '@/lib/cdn-image-loader';
import type { Metadata } from 'next';
import { MDXRemote } from 'next-mdx-remote/rsc';
import Image from 'next/image';
import { notFound } from 'next/navigation';
import rehypeAutolinkHeadings from 'rehype-autolink-headings';
import rehypeExternalLinks from 'rehype-external-links';
import rehypePrettyCode from 'rehype-pretty-code';
import rehypeSlug from 'rehype-slug';
import remarkGfm from 'remark-gfm';
import remarkSmartypants from 'remark-smartypants';

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

function ArticleJsonLd({ post }: { post: NonNullable<Awaited<ReturnType<typeof getPostBySlug>>> }) {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'Article',
    headline: post.title,
    description: post.description,
    datePublished: post.date,
    dateModified: post.updated ?? post.date,
    author: { '@type': 'Person', name: post.author },
    publisher: { '@type': 'Organization', name: 'Riven', url: 'https://getriven.io' },
  };
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replaceAll('</', '<\\u002f') }}
    />
  );
}

function BreadcrumbJsonLd({
  post,
}: {
  post: NonNullable<Awaited<ReturnType<typeof getPostBySlug>>>;
}) {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Home', item: 'https://getriven.io' },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Blog',
        item: 'https://getriven.io/resources/blog',
      },
      {
        '@type': 'ListItem',
        position: 3,
        name: CATEGORY_LABELS[post.category],
        item: `https://getriven.io/resources/blog/category/${post.category}`,
      },
      { '@type': 'ListItem', position: 4, name: post.title },
    ],
  };
  return (
    <script
      type="application/ld+json"
      dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd).replaceAll('</', '<\\u002f') }}
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

      {/* ── Dark hero header ── */}
      <div className="paper-lite relative mt-18 bg-foreground/90 text-background">
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
        <div className="mx-auto max-w-5xl px-6 pt-10 pb-12 lg:px-8">
          <Breadcrumbs category={post.category} postTitle={post.title} variant="inverse" />

          <header className="mt-4 max-w-3xl">
            <span className="font-mono text-xs font-bold tracking-widest text-background/50 uppercase">
              {CATEGORY_LABELS[post.category]}
            </span>

            <h1 className="mt-4 font-[family-name:var(--font-instrument-serif)] text-4xl tracking-tight lg:text-5xl">
              {post.title}
            </h1>

            <p className="mt-4 text-lg leading-snug text-background/60">{post.description}</p>

            <div className="mt-6 flex flex-wrap items-center gap-x-4 gap-y-1 border-t border-background/10 pt-5">
              <span className="font-mono text-xs tracking-widest text-background/50 uppercase">
                {post.author}
              </span>
              {post.tags.map((tag) => (
                <span
                  key={tag}
                  className="font-mono text-xs tracking-widest text-background/40 uppercase"
                >
                  {tag.replace(/-/g, ' ')}
                </span>
              ))}
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

        {/* Extra bottom padding when cover image will overlap */}
        {post.coverImage && <div className="pb-44 sm:pb-52 lg:pb-64" />}
      </div>

      {/* Cover image — straddles the dark/light boundary */}
      {post.coverImage && (
        <div className="relative z-10 mx-auto -mt-44 max-w-5xl px-6 sm:-mt-52 lg:-mt-64 lg:px-8">
          <div className="overflow-hidden rounded-lg shadow-lg shadow-foreground/40 dark:shadow-none">
            <Image
              src={getCdnUrl(post.coverImage)}
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

      {/* ── Content body ── */}
      <main className="mx-auto max-w-5xl px-6 pt-12 pb-20 lg:px-8">
        <div className="lg:grid lg:grid-cols-[1fr_200px] lg:gap-12">
          <article className="max-w-prose">
            {/* Mobile TOC */}
            <div className="lg:hidden">
              <TableOfContents headings={post.headings} />
            </div>

            <MDXRemote
              source={post.content}
              components={mdxComponents}
              options={{
                mdxOptions: {
                  remarkPlugins: [remarkGfm, remarkSmartypants],
                  rehypePlugins: [
                    rehypeSlug,
                    [rehypeAutolinkHeadings, { behavior: 'wrap' }],
                    [rehypePrettyCode, { theme: 'github-dark-default' }],
                    [rehypeExternalLinks, { target: '_blank', rel: ['noopener', 'noreferrer'] }],
                  ],
                },
              }}
            />
          </article>

          {/* Desktop TOC sidebar */}
          <aside className="hidden lg:block">
            <div className="sticky top-24">
              <TableOfContents headings={post.headings} />
            </div>
          </aside>
        </div>

        <RelatedPosts posts={related} />
      </main>
    </>
  );
}
