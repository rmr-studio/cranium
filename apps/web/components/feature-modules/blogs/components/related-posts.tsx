import type { BlogPostMeta } from '@/lib/blog-types';
import Link from 'next/link';

export function RelatedPosts({ posts }: { posts: BlogPostMeta[] }) {
  if (posts.length === 0) return null;

  return (
    <section className="mt-16 border-t border-border pt-12">
      <h2 className="mb-6 font-[family-name:var(--font-instrument-serif)] text-2xl italic text-muted-foreground">
        More from the blog
      </h2>
      <div className="grid gap-6 sm:grid-cols-2 lg:grid-cols-3">
        {posts.map((post) => (
          <Link key={post.slug} href={`/resources/blog/${post.slug}`} className="group block">
            <article>
              <span className="font-mono text-xs uppercase tracking-widest text-muted-foreground">
                {new Date(post.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
              </span>
              <h3 className="mt-2 font-semibold tracking-tight transition-colors group-hover:text-muted-foreground">
                {post.title}
              </h3>
              <p className="mt-1 text-sm text-muted-foreground line-clamp-2">{post.description}</p>
            </article>
          </Link>
        ))}
      </div>
    </section>
  );
}
