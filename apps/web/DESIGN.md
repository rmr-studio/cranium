# Design System — Riven

## Product Context

- **What this is:** Unified business tooling SaaS platform connecting all tools through integrations and flexible data modelling
- **Who it's for:** Small-to-medium business operators managing clients, entities, invoices, workflows, and cross-domain data
- **Space/industry:** B2B SaaS — CRM, project management, business operations. Peers: Attio, Lightfield, Midday, Linear
- **Project type:** Web application (dashboard, data tables, editors, settings)

## Aesthetic Direction

- **Direction:** Typography-Forward Monochrome
- **Decoration level:** Minimal — no gradients, no patterns, no textures. Depth comes from surface layering (z-level backgrounds) and subtle shadows, not decoration.
- **Mood:** Calm, authoritative, precise. Like a well-set book. The type does the heavy lifting. Color is earned, not sprinkled. Space creates hierarchy where color would in other products.
- **Reference sites:** [Midday](https://midday.ai) (typography confidence, monochrome commitment, editorial density), [Lightfield](https://crm.lightfield.app) (generous whitespace, surface layering, oklch-native tokens)
- **Preview:** See `docs/designs/design-system-preview.html` for a rendered specimen page with theme toggle

## Typography

All fonts are loaded via `next/font/google` in `app/layout.tsx`.

- **Display/Hero:** Geist 700, 40-56px, `tracking-tighter` (`-0.05em`), `leading-none` — tight, confident, engineered. Used for hero numbers, page-level data displays (revenue totals, counts).
- **H1:** Geist 700, 32px, `tracking-tighter` (`-0.05em`), `leading-none` — page titles.
- **H2:** Geist 600, 24px, `tracking-tight` (`-0.025em`), `leading-none` — section headings.
- **H3:** Geist 600, 18px, `tracking-tight` (`-0.025em`), `leading-none` — sub-sections.
- **Body:** Geist 400, 14px, `tracking-tight` (`-0.025em`), `leading-none` — default text. Compact, dense reading.
- **UI/Labels:** Geist 500, 13px, `tracking-tight`, `leading-none` — sidebar items, table headers, form labels. Slightly heavier than body for scannability.
- **Data/Tables:** Geist 400, 13-14px, `tracking-tight`, `leading-none`, `font-variant-numeric: tabular-nums` — numbers align in columns. Dates, counts, currency.
- **Code:** Geist Mono 400, `tracking-tight`, `leading-none` — code blocks, technical values.
- **Accent display:** Instrument Serif 400, 32-48px, `tracking-tight`, `leading-none` — editorial moments on marketing/landing pages. variant available.
- **System labels:** Space Mono 700, 11-14px, `text-transform: uppercase`, `tracking-wide` (`0.05em`), `leading-none` — section headers in sidebar panels ("Records", "Resources", "Workspace"). Exception: keeps wide tracking for uppercase legibility.
- **Loading:** Google Fonts via `next/font` (Geist, Geist Mono, Instrument Serif, Space Mono). CSS variables: `--font-geist-sans`, `--font-geist-mono`, `--font-instrument-serif`, `--font-space-mono`.
- **Scale:** Display(56) → H1(32) → H2(24) → H3(18) → Body(14) → UI(13) → Small(12) → Micro(11)

### Typography Rules

- All text uses `leading-none` (line-height: 1) by default. This creates the compact, dense feel across the entire application.
- Display and H1 use `tracking-tighter` (`-0.05em`). All other text uses `tracking-tight` (`-0.025em`). Exception: uppercase system labels keep `tracking-wide` for legibility.
- Never use `leading-relaxed`, `leading-normal`, or any loose line-height. The tight, compact density is intentional.
- Numbers should always use `tabular-nums` in data contexts (tables, stats, timestamps).
- Never use Geist above weight 700. The font loses clarity at 800-900.

## Color

- **Approach:** Restrained monochrome — zero chroma for the core system. The only hue in the product comes from semantic meaning.
- **All values use oklch.** Never use hex or hsl for new tokens.

### Core Palette (Light)

| Token                  | Value              | Usage                                           |
| ---------------------- | ------------------ | ----------------------------------------------- |
| `--background`         | `oklch(0.985 0 0)` | Page background                                 |
| `--card` / elevated    | `oklch(1 0 0)`     | Cards, panels, popovers — sits above background |
| `--muted`              | `oklch(0.97 0 0)`  | Hover states, secondary backgrounds             |
| `--border`             | `oklch(0.922 0 0)` | Borders, dividers                               |
| `--muted-foreground`   | `oklch(0.556 0 0)` | Secondary text, timestamps, placeholders        |
| `--content`            | `oklch(0.375 0 0)` | Body text                                       |
| `--heading`            | `oklch(0.28 0 0)`  | Headings, emphasis                              |
| `--foreground`         | `oklch(0.145 0 0)` | Primary text, icon rail background              |
| `--primary`            | `oklch(0.205 0 0)` | Buttons, interactive elements                   |
| `--primary-foreground` | `oklch(0.985 0 0)` | Text on primary                                 |

### Semantic Colors

| Token           | Value                       | Usage                  |
| --------------- | --------------------------- | ---------------------- |
| `--destructive` | `oklch(0.577 0.245 27.325)` | Delete actions, errors |
| `--edit`        | `oklch(0.8366 0.117 66.29)` | Edit mode indicators   |
| `--archive`     | `oklch(0.585 0.204 277.12)` | Archive actions        |
| `--success`     | `oklch(0.65 0.17 145)`      | Success states         |
| `--warning`     | `oklch(0.75 0.15 75)`       | Warning states         |

### Dark Mode

Invert the neutral scale. Reduce shadow opacity, increase shadow spread. Keep semantic colors at the same hue, adjust lightness for contrast.

### Color Rules

- Color is rare and meaningful. If something has color, it has semantic intent.
- Badges, tags, and status indicators are the primary carriers of color.
- Do not use color for decoration, backgrounds, or visual interest. The monochrome palette IS the visual identity.
- Chart colors exist for data visualization only (see `--chart-1` through `--chart-5` in globals.css).

## Spacing

- **Base unit:** 4px
- **Density:** Comfortable, trending spacious (Lightfield influence)
- **Scale:** `2xs(2px)` `xs(4px)` `sm(8px)` `md(16px)` `lg(24px)` `xl(32px)` `2xl(48px)` `3xl(64px)`

### Spacing Rules

- Content areas (settings pages, editors, detail views) use generous padding: `px-6 py-6` minimum, `px-8 py-8` preferred.
- Data-dense views (tables, lists) use tighter spacing: `px-4 py-3` for cells.
- Sidebar panel items use `px-3 py-2` (matching current entities panel pattern).
- Section gaps between content blocks: `32px` minimum, `48px` for major sections.
- The product should feel spacious, not cramped. When in doubt, add more space.

## Layout

- **Approach:** Grid-disciplined — strict alignment, generous margins
- **Structure:** Icon rail (56px, `--icon-rail-width: 3.5rem`) + collapsible sub-panel (400px, `--sub-panel-width: 25rem`) + main content (flex-1)
- **Header height:** 56px (`--header-height: 3.5rem`)
- **Max content width:** `max-w-5xl` for settings/forms, full-width for data tables
- **Grid:** Main content uses single-column with max-width constraints. Dashboard cards use responsive grid (`grid-cols-1 md:grid-cols-2 lg:grid-cols-3`).

### Border Radius

Tightened from 0.625rem to 0.5rem base.
| Token | Value | Usage |
|-------|-------|-------|
| `--radius-sm` | `4px` | Buttons, inputs, badges, interactive elements |
| `--radius-md` | `6px` | Small cards, popovers |
| `--radius-lg` | `8px` | Cards, panels, dialogs |
| `--radius-xl` | `12px` | Large containers, app mockup frames |
| `--radius-full` | `9999px` | Avatars, status dots |

### Shadows (new — Lightfield influence)

Use subtle box-shadows for depth instead of heavy borders. Creates the "floating" card feel.
| Token | Value | Usage |
|-------|-------|-------|
| `--shadow-sm` | `0px 1px 3px oklch(0 0 0 / 0.04)` | Buttons, inputs with elevation |
| `--shadow-md` | `0px 4px 12px oklch(0 0 0 / 0.06)` | Cards, popovers, dropdowns |
| `--shadow-lg` | `0px 8px 24px oklch(0 0 0 / 0.08)` | Dialogs, floating panels |

## Motion

- **Approach:** Minimal-functional — only transitions that aid comprehension. No spring physics, no bouncing, no choreography.
- **Easing:** `ease-out` for enters, `ease-in` for exits, `ease-in-out` for movement
- **Duration:** `micro(50-100ms)` button hover states | `short(150-200ms)` panel transitions, dropdowns | `medium(250-350ms)` page transitions, drawer slides | `long(400-600ms)` skeleton fade-in
- **Panel slide:** 200ms linear (current, keep)
- **Library:** Framer Motion for layout animations, Tailwind transitions for micro-interactions

### Motion Rules

- Every animation must serve comprehension. If removing it wouldn't confuse the user, remove it.
- No entry animations on page load (no fade-in-up for content blocks). Content appears instantly.
- Skeleton loaders use `animate-pulse` (subtle, existing pattern).

## Icon Rail

The dark inverted icon rail is Riven's structural signature. It establishes the app's visual frame.

- **Background:** `var(--foreground)` (dark on light, creates the inversion)
- **Icons:** Lucide icons at `size-5` (20px), `text-background/60` default, `text-background` active
- **Active state:** `bg-background/15` with full opacity icon
- **Hover state:** `bg-background/10` with increased opacity
- **Width:** 56px fixed

## Decisions Log

| Date       | Decision                        | Rationale                                                                                                   |
| ---------- | ------------------------------- | ----------------------------------------------------------------------------------------------------------- |
| 2026-03-20 | Initial design system created   | Created by /design-consultation based on Midday (typography) + Lightfield (spacing/surface) references      |
| 2026-03-20 | Keep Geist as primary font      | Already loaded, matches aesthetic. At display sizes with tight tracking, achieves Midday's editorial feel.  |
| 2026-03-20 | Tighten radius to 0.5rem base   | Matches Midday's tighter radius. 4px on interactive, 8px on containers.                                     |
| 2026-03-20 | Add shadow tokens               | Lightfield uses shadows for depth instead of borders. Creates modern floating-card feel.                    |
| 2026-03-20 | Button radius: 4px (sm)         | Compromise between Midday's 0px (too bold for SaaS) and current 10px (too rounded). Sharp but approachable. |
