import { getAllPosts } from '@/lib/blog';

const BASE_URL = 'https://getcranium.io';

export async function GET() {
  const posts = await getAllPosts();
  const recent = posts.slice(0, 20);

  const escapeCdata = (s: string) => s.replaceAll(']]>', ']]]]><![CDATA[>');

  const items = recent
    .map(
      (post) => `
    <item>
      <title><![CDATA[${escapeCdata(post.title)}]]></title>
      <link>${BASE_URL}/resources/blog/${post.slug}</link>
      <guid isPermaLink="true">${BASE_URL}/resources/blog/${post.slug}</guid>
      <description><![CDATA[${escapeCdata(post.description)}]]></description>
      <pubDate>${new Date(post.date).toUTCString()}</pubDate>
      <author>jared@cranium.software (${post.author})</author>
    </item>`,
    )
    .join('');

  const feed = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:atom="http://www.w3.org/2005/Atom">
  <channel>
    <title>Cranium Blog</title>
    <link>${BASE_URL}/resources/blog</link>
    <description>Tool comparisons, operational intelligence, and cross-domain analytics insights.</description>
    <language>en-us</language>
    <lastBuildDate>${new Date().toUTCString()}</lastBuildDate>
    <atom:link href="${BASE_URL}/resources/blog/rss.xml" rel="self" type="application/rss+xml"/>
    ${items}
  </channel>
</rss>`;

  return new Response(feed.trim(), {
    headers: {
      'Content-Type': 'application/rss+xml; charset=utf-8',
      'Cache-Control': 'public, max-age=3600, s-maxage=3600',
    },
  });
}
