# Blog Publishing Guide

How to write, format, and publish blog posts on getriven.io.

## Quick Start

1. Create a new `.mdx` file in `apps/web/content/blog/`
2. Add frontmatter (see template below)
3. Write your content in MDX
4. Deploy (build + push)

The blog is statically generated at build time. Publishing a new post means adding a file and deploying.

## File Location

```
apps/web/content/blog/
  your-post-slug.mdx
```

The filename becomes the URL slug: `your-post-slug.mdx` renders at `/blog/your-post-slug`.

### Slug Rules

- Lowercase, hyphen-separated
- Match the target keyword phrase (e.g., `intercom-vs-zendesk`)
- No dates in URLs (content is evergreen, dates make refreshed content look old)
- Keep under 60 characters

## Frontmatter Template

Every post requires this YAML frontmatter block at the top of the file:

```yaml
---
title: "Your Post Title Here"
description: "150-160 character meta description. Include target keyword. This appears in search results and social previews."
date: "2026-03-24"
updated: "2026-03-24"
author: "Jared Tucker"
category: "tool-comparison"
tags: ["tag-one", "tag-two", "tag-three"]
coverImage: "images/blog/your-post-slug.webp"
featured: false
---
```

### Required Fields

| Field | Type | Description |
|-------|------|-------------|
| `title` | string | Post title. Target keyword should be in the title. Under 60 chars for SEO. |
| `description` | string | Meta description. 150-160 chars. Appears in search results and OG previews. |
| `date` | string | Publish date in `YYYY-MM-DD` format. |
| `author` | string | Author name. |
| `category` | string | One of the valid categories (see below). |
| `tags` | string[] | Array of lowercase tags for related posts and search. |

### Optional Fields

| Field | Type | Default | Description |
|-------|------|---------|-------------|
| `updated` | string | same as `date` | Last updated date. Shows "Last updated" on page if different from `date`. Visible update dates get 1.8x more AI citations. |
| `coverImage` | string | none | Path to cover image in Cloudflare CDN (relative to CDN root, e.g., `images/blog/post-name.webp`). Used in the featured hero on the blog index. |
| `featured` | boolean | `false` | Set to `true` to pin this post as the hero on the blog index. Only one post should be featured at a time. If none is featured, the most recent post becomes the hero. |

### Valid Categories

| Category Slug | Display Label | Use For |
|--------------|---------------|---------|
| `tool-comparison` | Tool Comparisons | "[Tool A] vs [Tool B]" posts comparing tools in categories Riven integrates with |
| `operational-intelligence` | Operational Intelligence | "How to [do X] across [multiple tools]" operational content |
| `category-definition` | Category Definitions | "What is [concept]?" definitional content that establishes vocabulary |
| `changelog` | Changelog | Product updates, feature releases, version notes |

## Post Structure

Follow this structure for every post. Optimized for human readability and AI extraction.

```
1. TITLE (H1) — automatically rendered from frontmatter, don't add one manually

2. FRONT-LOADED ANSWER (First 50 words)
   Clear, direct answer to the post's core question.
   This is the snippet AI models extract. No preamble.

3. COMPARISON TABLE (if applicable)
   Markdown table comparing key dimensions.

4. BODY SECTIONS (H2s as questions)
   Format headings as questions readers ask.
   Each section: answer first, then supporting detail.

5. CROSS-DOMAIN CONNECTION (one section)
   Where Riven fits — how these tools connect to the broader stack.
   Not a sales pitch. A genuine observation about data fragmentation.

6. FAQ SECTION (## Frequently Asked Questions)
   3-5 genuine questions with 2-3 sentence answers.
   Use ### for each question (H3 under the H2).
   Gets extracted as FAQ schema markup for rich results.

7. LAST UPDATED DATE — handled automatically from frontmatter `updated` field.
```

## Writing in MDX

MDX is Markdown with JSX component support. Standard Markdown works — tables, lists, code blocks, links, images all render as expected.

### Headings

Use `##` (H2) for main sections and `###` (H3) for sub-sections. These automatically get:
- Anchor links (hover to see the link icon)
- Table of contents entries (for posts with multiple H2s)
- Heading IDs for deep linking

Don't use `#` (H1) — the post title from frontmatter is the H1.

### Tables

Standard Markdown tables render with styled headers and borders:

```markdown
| Feature | Tool A | Tool B |
|---------|--------|--------|
| Pricing | $39/mo | $19/mo |
| API     | REST   | GraphQL |
```

### Code Blocks

Fenced code blocks get syntax highlighting and a copy button:

````markdown
```typescript
const result = await fetchData();
```
````

### Blockquotes

Blockquotes render in Instrument Serif italic (editorial accent):

```markdown
> This is a pull quote or editorial aside.
```

### Interactive Comparison Table

For richer comparison tables (sortable, with boolean indicators), use the `ComparisonTable` component:

```mdx
<ComparisonTable
  columns={[
    { key: "feature", label: "Feature" },
    { key: "toolA", label: "Tool A" },
    { key: "toolB", label: "Tool B" },
  ]}
  rows={[
    { feature: "API Access", toolA: true, toolB: false },
    { feature: "Pricing", toolA: "$39/mo", toolB: "$19/mo" },
    { feature: "Webhooks", toolA: true, toolB: "Partial" },
  ]}
  highlightColumn="toolA"
/>
```

## Cover Images

Cover images appear on the featured hero card on the blog index page. They're stored in Cloudflare R2 and referenced by path.

- **Aspect ratio:** 16:9 (1200x675 recommended)
- **Format:** WebP preferred, PNG acceptable
- **Upload to:** Cloudflare R2 bucket at `images/blog/`
- **Reference in frontmatter:** `coverImage: "images/blog/your-post-slug.webp"`

Posts without a `coverImage` still work — the hero card just won't have an image.

## SEO Checklist

Before publishing:

- [ ] First 50 words contain a direct answer to the post's question
- [ ] Title includes target keyword, under 60 characters
- [ ] Description is 150-160 characters, includes target keyword
- [ ] H2/H3 headings are phrased as questions readers ask
- [ ] Comparison table present (for tool comparison posts)
- [ ] FAQ section with 3-5 questions and concise answers
- [ ] Riven mention limited to one clearly separated section
- [ ] No fluff intro — opens with the answer
- [ ] `updated` date set (visible update dates increase AI citations)
- [ ] Sources cited where applicable

## Publishing

Blog posts are published by deploying the site. There's no draft/preview system — if the MDX file exists in `content/blog/`, it's live after the next deploy.

### Workflow

1. Write the post in `apps/web/content/blog/your-slug.mdx`
2. Run `pnpm test` from `apps/web/` to verify the post parses correctly
3. Run `pnpm dev` to preview locally at `localhost:3000/blog/your-slug`
4. Commit and push
5. Deploy

### Setting a Featured Post

Set `featured: true` in the frontmatter of the post you want as the hero. Remove `featured: true` from any other post (only one should be featured). If no post is featured, the most recent by date is used.

## Content Categories and Topic Clusters

Posts are organized into topic clusters via categories. Each category has a page at `/blog/category/[category]`. Categories are only generated when at least one post exists in that category.

### Content Pillars (from strategy docs)

1. **Tool Comparisons** — "[Tool A] vs [Tool B]" for tools Riven integrates with. Target high-intent search queries. Include a "cross-domain connection" section.
2. **Operational Intelligence** — "How to [task] across [tools]". Address cross-tool visibility problems.
3. **Category Definitions** — "What is [concept]?" Define the vocabulary and mental models for cross-domain intelligence.
4. **Changelog** — Product updates and feature releases. Shorter format, version-focused.

## Updating Existing Posts

To update a post:

1. Edit the `.mdx` file
2. Update the `updated` field in frontmatter to today's date
3. Deploy

The "Last updated" date will appear on the post page, and the sitemap will reflect the new date. Content updated within 10 months gets significantly more AI citations.

## RSS Feed

The RSS feed at `/blog/rss.xml` automatically includes the 20 most recent posts. No manual action needed.

## Dynamic OG Images

Each post gets an auto-generated Open Graph image at `/api/og?slug=your-slug`. The image includes the post title, category, author, and read time on a dark branded background. No manual OG image creation needed.
