import { BlogHeroHeader } from '@/components/feature-modules/blogs/components/blog-hero-header';
import { Breadcrumbs } from '@/components/feature-modules/blogs/components/breadcrumbs';
import { ReadingProgress } from '@/components/feature-modules/blogs/components/reading-progress';
import { RelatedPosts } from '@/components/feature-modules/blogs/components/related-posts';
import { TableOfContents } from '@/components/feature-modules/blogs/components/table-of-contents';
import { mdxComponents } from '@/components/feature-modules/blogs/mdx/mdx-components';
import { getAllPosts, getPostBySlug, getRelatedPosts } from '@/lib/blog';
import { CATEGORY_LABELS } from '@/lib/blog-types';
import type { Metadata } from 'next';
import { MDXRemote } from 'next-mdx-remote/rsc';
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
    publisher: { '@type': 'Organization', name: 'Cranium', url: 'https://getcranium.io' },
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
      { '@type': 'ListItem', position: 1, name: 'Home', item: 'https://getcranium.io' },
      {
        '@type': 'ListItem',
        position: 2,
        name: 'Blog',
        item: 'https://getcranium.io/resources/blog',
      },
      {
        '@type': 'ListItem',
        position: 3,
        name: CATEGORY_LABELS[post.category],
        item: `https://getcranium.io/resources/blog/category/${post.category}`,
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

      <BlogHeroHeader
        post={post}
        variant="post"
        topSlot={<Breadcrumbs category={post.category} postTitle={post.title} variant="inverse" />}
        metaSlot={post.tags.map((tag) => (
          <span
            key={tag}
            className="font-mono text-xs tracking-widest text-background/40 uppercase"
          >
            {tag.replace(/-/g, ' ')}
          </span>
        ))}
      />

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
