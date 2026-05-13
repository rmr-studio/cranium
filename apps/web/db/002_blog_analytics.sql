-- Blog analytics table for getcranium.io
-- Tracks page views and engagement metrics for blog posts
-- Lightweight first-party analytics alongside PostHog

create table if not exists public.blog_post_views (
  id          uuid primary key default gen_random_uuid(),
  slug        text not null,
  referrer    text,
  user_agent  text,
  country     text,
  created_at  timestamptz not null default now()
);

-- RLS: service role only for inserts (server-side tracking), no public access
alter table public.blog_post_views enable row level security;

-- No anon policies — only the service role key can write to this table.
-- This prevents abuse from the client side.

-- Indexes for common queries
create index if not exists idx_blog_views_slug on public.blog_post_views (slug);
create index if not exists idx_blog_views_created on public.blog_post_views (created_at desc);
create index if not exists idx_blog_views_slug_date on public.blog_post_views (slug, created_at desc);
