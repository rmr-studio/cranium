import { BlogHeroHeader } from '@/components/feature-modules/blogs/components/blog-hero-header';
import { BlogSearch } from '@/components/feature-modules/blogs/components/blog-search';
import { CategoryPills } from '@/components/feature-modules/blogs/components/category-pills';
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

  const feedPosts = featured ? posts.filter((p) => p.slug !== featured.slug) : posts;

  return (
    <main className="min-h-screen overflow-x-clip">
      {featured && <BlogHeroHeader post={featured} variant="overview" />}
      <div className="mx-auto max-w-5xl px-6 pt-12 pb-20 lg:px-8">
        <CategoryPills categories={categories} />
        <BlogSearch posts={feedPosts} />
      </div>
    </main>
  );
}
