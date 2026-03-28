# Landing Page Database Scripts

SQL scripts for all Supabase tables used by the getriven.io landing page and blog.

These are separate from the core application database (`core/db/`). The landing page uses its own tables in the same Supabase project but with different RLS policies — anon access for waitlist signups, service-role-only for blog analytics.

## Scripts

| Script | Table | Purpose |
|--------|-------|---------|
| `001_waitlist_submissions.sql` | `waitlist_submissions` | Waitlist signups + survey responses from the multi-step form |
| `002_blog_analytics.sql` | `blog_post_views` | Blog post view tracking (server-side only) |

## Running Scripts

Scripts are idempotent (`create table if not exists`). Run them in order against the Supabase project:

```bash
# Via Supabase CLI
supabase db execute -f apps/web/db/001_waitlist_submissions.sql
supabase db execute -f apps/web/db/002_blog_analytics.sql
```

Or use the `db:sql` skill from `core/` to execute them.

## RLS Policies

- **waitlist_submissions**: Anon can insert and update (needed for client-side form submission)
- **blog_post_views**: No anon access — only the service role key can write (server-side API routes)
