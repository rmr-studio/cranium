import { CATEGORY_LABELS, type BlogPostMeta } from '@/lib/blog-types';
import Link from 'next/link';

export function BlogFeed({ posts }: { posts: BlogPostMeta[] }) {
  if (posts.length === 0) {
    return (
      <div className="py-16 text-center">
        <p className="text-lg text-muted-foreground">No posts published yet. Check back soon.</p>
        <Link href="/" className="mt-4 inline-block text-sm text-foreground underline underline-offset-4">
          Back to home
        </Link>
      </div>
    );
  }

  return (
    <div className="divide-y divide-border">
      {posts.map((post) => (
        <article key={post.slug} className="py-6 first:pt-0">
          <Link href={`/blog/${post.slug}`} className="group block">
            <div className="mb-2 flex items-center gap-2 font-mono text-xs uppercase tracking-widest text-muted-foreground">
              <time dateTime={post.date}>
                {new Date(post.date).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' })}
              </time>
              <span>&middot;</span>
              <span>{post.readTime} min</span>
            </div>
            <h3 className="text-lg font-semibold tracking-tight transition-colors group-hover:text-muted-foreground lg:text-xl">
              {post.title}
            </h3>
            <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground line-clamp-2">{post.description}</p>
            <div className="mt-3 flex items-center justify-between">
              <span className="font-mono text-xs uppercase tracking-widest text-muted-foreground">{post.author}</span>
              <span className="font-mono text-xs uppercase tracking-widest text-muted-foreground">
                {CATEGORY_LABELS[post.category]}
              </span>
            </div>
          </Link>
        </article>
      ))}
    </div>
  );
}
