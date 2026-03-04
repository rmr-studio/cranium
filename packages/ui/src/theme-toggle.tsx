'use client';

import { useTheme } from 'next-themes';
import { Button } from './button';
import { useMounted } from './use-mounted';

const themes = ['light', 'dark', 'amber'] as const;
type Theme = (typeof themes)[number];

const themeLabels: Record<Theme, string> = {
  light: 'Light',
  dark: 'Dark',
  amber: 'Amber',
};

export function ThemeToggle() {
  const { setTheme, resolvedTheme, theme } = useTheme();
  const mounted = useMounted();

  const activeTheme = (theme === 'system' ? resolvedTheme : theme) as Theme;

  const cycle = () => {
    const currentIndex = themes.indexOf(activeTheme);
    const next = themes[(currentIndex + 1) % themes.length];

    // Skip view transition when mobile nav is open — iOS re-composites
    // backdrop-filter during the crossfade, causing a visible blur flicker.
    if (document.startViewTransition && !document.querySelector('[data-mobile-nav]')) {
      document.startViewTransition(() => setTheme(next));
      return;
    }

    setTheme(next);
  };

  if (!mounted) {
    return (
      <Button
        disabled
        className="inline-flex items-center gap-2 rounded-full border border-border/50 bg-muted/50 px-2.5 py-1 font-mono text-xs tracking-widest text-muted-foreground"
      >
        <span className="flex gap-1">
          {themes.map((t) => (
            <span
              key={t}
              className="block h-1.5 w-1.5 rounded-full border border-muted-foreground/40"
            />
          ))}
        </span>
      </Button>
    );
  }

  return (
    <Button
      onClick={cycle}
      className="flex cursor-pointer items-center gap-2 rounded-full border border-border/50 bg-muted/50 px-2.5 py-1 font-mono text-xs tracking-widest text-muted-foreground transition-colors hover:bg-muted hover:text-foreground"
    >
      <span>{themeLabels[activeTheme] ?? 'Light'}</span>
      <span className="flex gap-1">
        {themes.map((t) => (
          <span
            key={t}
            className={`block h-1.5 w-1.5 rounded-full transition-colors ${
              t === activeTheme ? 'bg-foreground' : 'border border-muted-foreground/40'
            }`}
          />
        ))}
      </span>
      <span className="sr-only">Toggle theme</span>
    </Button>
  );
}
