import { getPostBySlug } from '@/lib/blog';
import { CATEGORY_LABELS } from '@/lib/blog-types';
import { ImageResponse } from 'next/og';
import type { NextRequest } from 'next/server';

export async function GET(request: NextRequest) {
  const slug = request.nextUrl.searchParams.get('slug');

  if (!slug) {
    return new ImageResponse(
      (
        <div
          style={{
            width: '100%',
            height: '100%',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: '#1a1a1a',
            color: '#ffffff',
            fontSize: 48,
            fontWeight: 700,
          }}
        >
          Riven Blog
        </div>
      ),
      { width: 1200, height: 630 },
    );
  }

  const post = await getPostBySlug(slug);
  if (!post) {
    return new Response('Not found', { status: 404 });
  }

  return new ImageResponse(
    (
      <div
        style={{
          width: '100%',
          height: '100%',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between',
          padding: '60px 80px',
          backgroundColor: '#0a0a0a',
          color: '#ffffff',
        }}
      >
        <div style={{ display: 'flex', flexDirection: 'column', gap: '16px' }}>
          <span
            style={{
              fontSize: 14,
              fontWeight: 700,
              textTransform: 'uppercase',
              letterSpacing: '0.1em',
              color: '#888',
            }}
          >
            {CATEGORY_LABELS[post.category]}
          </span>
          <span
            style={{
              fontSize: 48,
              fontWeight: 700,
              lineHeight: 1.1,
              letterSpacing: '-0.02em',
              maxWidth: '900px',
            }}
          >
            {post.title}
          </span>
        </div>
        <div
          style={{
            display: 'flex',
            justifyContent: 'space-between',
            alignItems: 'flex-end',
            fontSize: 16,
            color: '#888',
          }}
        >
          <span>{post.author} &middot; {post.readTime} min read</span>
          <span style={{ fontSize: 20, fontWeight: 700, color: '#fff' }}>Riven</span>
        </div>
      </div>
    ),
    {
      width: 1200,
      height: 630,
      headers: {
        'Cache-Control': 'public, max-age=604800, s-maxage=604800',
      },
    },
  );
}
