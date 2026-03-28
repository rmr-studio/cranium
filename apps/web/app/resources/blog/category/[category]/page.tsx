import { BlogFeed } from '@/components/feature-modules/blogs/components/blog-feed';
import { BlogHeroHeader } from '@/components/feature-modules/blogs/components/blog-hero-header';
import { CategoryPills } from '@/components/feature-modules/blogs/components/category-pills';
import { getCategories, getPostsByCategory } from '@/lib/blog';
import { CATEGORY_LABELS, type BlogCategory } from '@/lib/blog-types';
import type { Metadata } from 'next';
import { notFound } from 'next/navigation';

interface Props {
  params: Promise<{ category: string }>;
}

export async function generateStaticParams() {
  const categories = await getCategories();
  return categories.map((c) => ({ category: c.slug }));
}

export async function generateMetadata({ params }: Props): Promise<Metadata> {
  const { category } = await params;
  const label = CATEGORY_LABELS[category as BlogCategory];
  if (!label) return {};
  return {
    title: `${label} — Blog`,
    description: `Browse ${label.toLowerCase()} articles on the Riven blog.`,
  };
}

export default async function CategoryPage({ params }: Props) {
  const { category } = await params;
  if (!CATEGORY_LABELS[category as BlogCategory]) notFound();

  const [posts, categories] = await Promise.all([
    getPostsByCategory(category as BlogCategory),
    getCategories(),
  ]);

  if (posts.length === 0) notFound();

  const latest = posts[0];

  return (
    <main className="min-h-screen overflow-x-clip">
      <BlogHeroHeader
        post={latest}
        variant="category"
        topSlot={
          <h1 className="mb-14 font-mono text-xs font-bold tracking-widest uppercase">
            {CATEGORY_LABELS[category as BlogCategory]}
          </h1>
        }
      />

      <div className="mx-auto max-w-5xl px-6 pt-12 pb-20 lg:px-8">
        <CategoryPills categories={categories} />
        <BlogFeed posts={posts} />
      </div>
    </main>
  );
}
