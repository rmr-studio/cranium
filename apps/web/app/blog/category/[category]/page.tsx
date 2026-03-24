import { BlogFeed } from '@/components/feature-modules/blog/components/blog-feed';
import { CategoryPills } from '@/components/feature-modules/blog/components/category-pills';
import { CATEGORY_LABELS, type BlogCategory } from '@/lib/blog-types';
import { getCategories, getPostsByCategory } from '@/lib/blog';
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

  return (
    <main className="mx-auto max-w-5xl px-6 pb-20 pt-12 lg:px-8">
      <h1 className="mb-8 text-3xl font-bold tracking-tight">
        {CATEGORY_LABELS[category as BlogCategory]}
      </h1>
      <CategoryPills categories={categories} />
      <BlogFeed posts={posts} />
    </main>
  );
}
