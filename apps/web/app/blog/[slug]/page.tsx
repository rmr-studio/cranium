import { Breadcrumbs } from '@/components/feature-modules/blog/components/breadcrumbs';
import { ReadingProgress } from '@/components/feature-modules/blog/components/reading-progress';
import { RelatedPosts } from '@/components/feature-modules/blog/components/related-posts';
import { TableOfContents } from '@/components/feature-modules/blog/components/table-of-contents';
import { mdxComponents } from '@/components/feature-modules/blog/mdx/mdx-components';
import { CATEGORY_LABELS } from '@/lib/blog-types';
import { getAllPosts, getPostBySlug, getRelatedPosts } from '@/lib/blog';
import type { Metadata } from 'next';
import { MDXRemote } from 'next-mdx-remote/rsc';
import { notFound } from 'next/navigation';
import rehypeAutolinkHeadings from 'rehype-autolink-headings';
import rehypePrettyCode from 'rehype-pretty-code';
import rehypeSlug from 'rehype-slug';

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
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />;
}

function BreadcrumbJsonLd({ post }: { post: NonNullable<Awaited<ReturnType<typeof getPostBySlug>>> }) {
  const jsonLd = {
    '@context': 'https://schema.org',
    '@type': 'BreadcrumbList',
    itemListElement: [
      { '@type': 'ListItem', position: 1, name: 'Home', item: 'https://getriven.io' },
      { '@type': 'ListItem', position: 2, name: 'Blog', item: 'https://getriven.io/blog' },
      {
        '@type': 'ListItem',
        position: 3,
        name: CATEGORY_LABELS[post.category],
        item: `https://getriven.io/blog/category/${post.category}`,
      },
      { '@type': 'ListItem', position: 4, name: post.title },
    ],
  };
  return <script type="application/ld+json" dangerouslySetInnerHTML={{ __html: JSON.stringify(jsonLd) }} />;
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

      <main className="mx-auto max-w-5xl px-6 pb-20 pt-12 lg:px-8">
        <Breadcrumbs category={post.category} postTitle={post.title} />

        <div className="lg:grid lg:grid-cols-[1fr_200px] lg:gap-12">
          <article className="max-w-prose">
            <header className="mb-12">
              <span className="font-mono text-xs font-bold uppercase tracking-widest text-muted-foreground">
                {CATEGORY_LABELS[post.category]}
              </span>
              <h1 className="mt-3 text-3xl font-bold tracking-tight lg:text-4xl">
                {post.title}
              </h1>
              <div className="mt-4 flex items-center gap-3 font-mono text-xs uppercase tracking-widest text-muted-foreground">
                <time dateTime={post.date}>
                  {new Date(post.date).toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </time>
                <span>&middot;</span>
                <span>{post.readTime} min read</span>
                <span>&middot;</span>
                <span>By {post.author}</span>
              </div>
              {post.updated && post.updated !== post.date && (
                <p className="mt-2 text-xs text-muted-foreground">
                  Last updated:{' '}
                  {new Date(post.updated).toLocaleDateString('en-US', {
                    month: 'short',
                    day: 'numeric',
                    year: 'numeric',
                  })}
                </p>
              )}
            </header>

            <MDXRemote
              source={post.content}
              components={mdxComponents}
              options={{
                mdxOptions: {
                  rehypePlugins: [
                    rehypeSlug,
                    [rehypeAutolinkHeadings, { behavior: 'wrap' }],
                    [rehypePrettyCode, { theme: 'github-dark-default' }],
                  ],
                },
              }}
            />
          </article>

          {/* TOC: renders as mobile dropdown OR desktop sticky sidebar via internal responsive logic */}
          <aside className="order-first lg:order-last">
            <div className="lg:sticky lg:top-24">
              <TableOfContents headings={post.headings} />
            </div>
          </aside>
        </div>

        <RelatedPosts posts={related} />
      </main>
    </>
  );
}
