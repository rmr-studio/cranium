import fs from 'fs';
import path from 'path';
import GithubSlugger from 'github-slugger';
import matter from 'gray-matter';
import readingTime from 'reading-time';
import type { BlogPost, BlogPostMeta, BlogCategory, Heading } from './blog-types';

const CONTENT_DIR = path.join(process.cwd(), 'content', 'blog');

export function calculateReadTime(content: string): number {
  const { minutes } = readingTime(content);
  return Math.max(1, Math.ceil(minutes));
}

function extractHeadings(content: string): Heading[] {
  const headingRegex = /^#{2,3}\s+(.+)$/gm;
  const slugger = new GithubSlugger();
  const headings: Heading[] = [];
  let match;

  while ((match = headingRegex.exec(content)) !== null) {
    const level = match[0].startsWith('###') ? 3 : 2;
    const text = match[1].trim();
    headings.push({ text, slug: slugger.slug(text), level: level as 2 | 3 });
  }

  return headings;
}

function parseFrontmatter(filePath: string): { meta: BlogPostMeta; content: string } | null {
  try {
    const raw = fs.readFileSync(filePath, 'utf-8');
    const { data, content } = matter(raw);

    const slug = path.basename(filePath, '.mdx');

    const meta: BlogPostMeta = {
      slug,
      title: data.title,
      description: data.description,
      date: data.date,
      updated: data.updated,
      author: data.author,
      category: data.category as BlogCategory,
      tags: data.tags ?? [],
      coverImage: data.coverImage,
      featured: data.featured ?? false,
      readTime: calculateReadTime(content),
    };

    return { meta, content };
  } catch (error) {
    console.error(`Failed to parse frontmatter for ${filePath}:`, error);
    return null;
  }
}

export async function getAllPosts(): Promise<BlogPostMeta[]> {
  if (!fs.existsSync(CONTENT_DIR)) return [];

  const files = fs.readdirSync(CONTENT_DIR).filter((f) => f.endsWith('.mdx'));

  const posts = files
    .map((file) => parseFrontmatter(path.join(CONTENT_DIR, file)))
    .filter((p): p is NonNullable<typeof p> => p !== null)
    .map((p) => p.meta)
    .sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime());

  return posts;
}

export async function getPostBySlug(slug: string): Promise<BlogPost | null> {
  if (!/^[a-zA-Z0-9_-]+$/.test(slug)) return null;
  const filePath = path.join(CONTENT_DIR, `${slug}.mdx`);
  const parsed = parseFrontmatter(filePath);
  if (!parsed) return null;

  return {
    ...parsed.meta,
    content: parsed.content,
    headings: extractHeadings(parsed.content),
  };
}

export async function getCategories(): Promise<{ slug: BlogCategory; count: number }[]> {
  const posts = await getAllPosts();
  const counts = new Map<BlogCategory, number>();

  for (const post of posts) {
    counts.set(post.category, (counts.get(post.category) ?? 0) + 1);
  }

  return Array.from(counts.entries())
    .map(([slug, count]) => ({ slug, count }))
    .sort((a, b) => b.count - a.count);
}

export async function getRelatedPosts(
  currentSlug: string,
  currentTags: string[],
  limit = 3,
): Promise<BlogPostMeta[]> {
  const posts = await getAllPosts();
  const currentTagSet = new Set(currentTags);

  const scored = posts
    .filter((p) => p.slug !== currentSlug)
    .map((post) => {
      const overlap = post.tags.filter((t) => currentTagSet.has(t)).length;
      return { post, overlap };
    })
    .sort((a, b) => b.overlap - a.overlap || new Date(b.post.date).getTime() - new Date(a.post.date).getTime());

  return scored.slice(0, limit).map((s) => s.post);
}

export async function getFeaturedPost(): Promise<BlogPostMeta | null> {
  const posts = await getAllPosts();
  return posts.find((p) => p.featured) ?? posts[0] ?? null;
}

export async function getPostsByCategory(category: BlogCategory): Promise<BlogPostMeta[]> {
  const posts = await getAllPosts();
  return posts.filter((p) => p.category === category);
}
