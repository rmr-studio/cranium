# @cranium/web

Marketing website and landing page.

## Tech stack

- Next.js 16 (App Router)
- Tailwind CSS 4 + Framer Motion
- Fonts: Geist, Instrument Serif, Space Mono, Geist Mono
- Media served from Cloudflare CDN via `getCdnUrl()`
- React Email for transactional templates

## Development

```sh
# From the monorepo root
pnpm --filter @cranium/web dev
```

Runs on [http://localhost:3000](http://localhost:3000).

### Environment variables

Create `apps/web/.env.local`:

```env
NEXT_PUBLIC_SUPABASE_URL=https://your-project.supabase.co
NEXT_PUBLIC_SUPABASE_ANON_KEY=your-anon-key
NEXT_PUBLIC_SITE_URL=http://localhost:3000
NEXT_PUBLIC_CDN_URL=https://cdn.cranium.software
```

## Project structure

```
app/              # App Router pages and layouts
components/
  feature-modules/
    hero/         # Hero section
    landing/      # Landing page sections
    blogs/        # Blog components
    story/        # Story/about section
  navbar/         # Navigation
  ui/             # Shared UI components
emails/           # React Email templates
lib/              # Utilities and helpers
public/           # Static assets
```
