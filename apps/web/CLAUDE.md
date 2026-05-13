# CLAUDE.md — Cranium Landing Page + Marketing

## Importing

Always import components and files using the @/alias rather than relative path imports

## Design System

Always read `DESIGN.md` before making any visual or UI decisions. All font choices, colors, spacing, border radius, shadows, and aesthetic direction are defined there. Do not deviate without explicit user approval. A rendered preview is available at `./design-system-preview.html`.

## Styling Rules

- **Always use Tailwind's built-in utility classes** — never use arbitrary values (e.g., `text-[11px]`, `w-[200px]`) when a standard class exists (`text-xs`, `w-52`). Arbitrary values are a last resort for truly custom one-off values with no Tailwind equivalent.
- Tailwind 4 with CSS-based config in `app/globals.css` (no `tailwind.config.ts`).
- Color tokens via CSS custom properties using oklch. Semantic tokens: `--background`, `--foreground`, `--card`, `--primary`, `--muted`, `--destructive`, `--edit`, `--archive`, plus sidebar and chart variants.
- Dark mode: class-based toggling via `next-themes`. `.dark` class on `<html>`. Custom variant: `@custom-variant dark (&:is(.dark *))`.
- Responsive: mobile-first with `sm:`, `md:`, `lg:` breakpoints.
- Animation: Framer Motion (`framer-motion` / `motion`) + Tailwind transitions + `tw-animate-css`.
- Fonts: Geist (primary), Instrument Serif (editorial accent), Space Mono (system labels), Geist Mono (code). Loaded via `next/font/google`. See DESIGN.md for full typography spec.

## Media Rendering

- All static media is stored in an external Cloudflare storage bucket accessed via `NEXT_PUBLIC_CDN_URL`.
- Use `getCdnUrl(path)` from `@/lib/cdn-image-loader` to build full URLs for any media asset. Paths are always prefixed with `images/` (e.g., `images/hero-bg.webp`).
- For Next.js `<Image>` components, pass `cdnImageLoader` as the `loader` prop (also from `@/lib/cdn-image-loader`).
- For multi-format images (avif/webp), use a `<picture>` element with `<source>` tags and build srcSets with `getCdnUrl`. See `components/feature-modules/hero/components/hero-background.tsx` for a reference implementation.
