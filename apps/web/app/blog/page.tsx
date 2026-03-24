import { BlogHero } from '@/components/feature-modules/blog/components/blog-hero';
import { BlogSearch } from '@/components/feature-modules/blog/components/blog-search';
import { CategoryPills } from '@/components/feature-modules/blog/components/category-pills';
import { getAllPosts, getCategories, getFeaturedPost } from '@/lib/blog';
import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Blog',
  description:
    'Tool comparisons, operational intelligence, and cross-domain analytics insights for scaling businesses.',
  openGraph: {
    title: 'Blog | Riven',
    description:
      'Tool comparisons, operational intelligence, and cross-domain analytics insights for scaling businesses.',
  },
};

export default async function BlogPage() {
  const [posts, categories, featured] = await Promise.all([
    getAllPosts(),
    getCategories(),
    getFeaturedPost(),
  ]);

  const feedPosts = featured
    ? posts.filter((p) => p.slug !== featured.slug)
    : posts;

  return (
    <main className="mx-auto max-w-5xl px-6 pb-20 pt-12 lg:px-8">
      {featured && <BlogHero post={featured} />}

      <div className="mt-12">
        <CategoryPills categories={categories} />
      </div>

      <BlogSearch posts={feedPosts} />
    </main>
  );
}
