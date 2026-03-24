import { describe, it, expect } from 'vitest';
import {
  getAllPosts,
  getPostBySlug,
  getCategories,
  getRelatedPosts,
  getFeaturedPost,
  calculateReadTime,
} from '@/lib/blog';

describe('calculateReadTime', () => {
  it('returns minutes based on 200 words/minute', () => {
    const words = Array(1000).fill('word').join(' ');
    expect(calculateReadTime(words)).toBe(5);
  });

  it('returns 1 minute minimum for short content', () => {
    expect(calculateReadTime('short')).toBe(1);
  });

  it('rounds up to nearest minute', () => {
    const words = Array(250).fill('word').join(' ');
    expect(calculateReadTime(words)).toBe(2);
  });
});

describe('getAllPosts', () => {
  it('returns posts sorted by date descending', async () => {
    const posts = await getAllPosts();
    expect(posts.length).toBeGreaterThan(0);
    for (let i = 1; i < posts.length; i++) {
      expect(new Date(posts[i - 1].date).getTime())
        .toBeGreaterThanOrEqual(new Date(posts[i].date).getTime());
    }
  });

  it('includes readTime on every post', async () => {
    const posts = await getAllPosts();
    for (const post of posts) {
      expect(post.readTime).toBeGreaterThanOrEqual(1);
    }
  });
});

describe('getPostBySlug', () => {
  it('returns a post with content and headings for a valid slug', async () => {
    const post = await getPostBySlug('intercom-vs-zendesk');
    expect(post).not.toBeNull();
    expect(post!.title).toContain('Intercom');
    expect(post!.content).toBeTruthy();
    expect(post!.headings.length).toBeGreaterThan(0);
  });

  it('returns null for an invalid slug', async () => {
    const post = await getPostBySlug('does-not-exist');
    expect(post).toBeNull();
  });
});

describe('getCategories', () => {
  it('returns unique categories from published posts', async () => {
    const categories = await getCategories();
    expect(categories.length).toBeGreaterThan(0);
    const unique = new Set(categories.map((c) => c.slug));
    expect(unique.size).toBe(categories.length);
  });
});

describe('getRelatedPosts', () => {
  it('excludes the current post', async () => {
    const related = await getRelatedPosts('intercom-vs-zendesk', ['support-tools'], 3);
    expect(related.every((p) => p.slug !== 'intercom-vs-zendesk')).toBe(true);
  });

  it('returns at most the requested limit', async () => {
    const related = await getRelatedPosts('intercom-vs-zendesk', ['support-tools'], 3);
    expect(related.length).toBeLessThanOrEqual(3);
  });
});

describe('getFeaturedPost', () => {
  it('returns the post with featured: true if one exists', async () => {
    const featured = await getFeaturedPost();
    expect(featured).not.toBeNull();
    expect(featured!.featured).toBe(true);
  });
});
