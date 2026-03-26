'use client';

import { Skeleton } from '@/components/ui/skeleton';
import { cn } from '@riven/utils';
import { Monitor, Moon, Sun } from 'lucide-react';
import { useTheme } from 'next-themes';
import { useEffect, useState } from 'react';

const THEMES = [
  { value: 'light', label: 'Light', icon: Sun },
  { value: 'dark', label: 'Dark', icon: Moon },
  { value: 'amber', label: 'Amber', icon: Monitor },
] as const;

export function AppearanceSection() {
  const { theme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => { setMounted(true); }, []);

  if (!mounted) {
    return (
      <section className="space-y-6">
        <div>
          <h2 className="text-2xl font-semibold tracking-tight">Appearance</h2>
          <p className="text-sm text-muted-foreground">
            Customize the look and feel of your platform
          </p>
        </div>
        <div className="space-y-3">
          <h3 className="text-sm font-medium">Theme</h3>
          <p className="text-sm text-muted-foreground">
            Select a theme to personalize your platform&apos;s appearance
          </p>
          <div className="grid grid-cols-3 gap-4">
            {THEMES.map((t) => (
              <Skeleton key={t.value} className="aspect-[4/3] rounded-lg" />
            ))}
          </div>
        </div>
      </section>
    );
  }

  return (
    <section className="space-y-6">
      <div>
        <h2 className="text-2xl font-semibold tracking-tight">Appearance</h2>
        <p className="text-sm text-muted-foreground">
          Customize the look and feel of your platform
        </p>
      </div>

      <div className="space-y-3">
        <h3 className="text-sm font-medium">Theme</h3>
        <p className="text-sm text-muted-foreground">
          Select a theme to personalize your platform&apos;s appearance
        </p>
        <div className="grid grid-cols-3 gap-4">
          {THEMES.map((t) => {
            const isActive = theme === t.value;
            const Icon = t.icon;
            return (
              <button
                key={t.value}
                type="button"
                onClick={() => setTheme(t.value)}
                className={cn(
                  'group flex cursor-pointer flex-col items-center gap-2 rounded-lg border-2 p-1 transition-colors',
                  isActive
                    ? 'border-primary'
                    : 'border-border/50 hover:border-border',
                )}
              >
                <ThemePreview variant={t.value} />
                <span className="flex items-center gap-1.5 pb-1 text-xs font-medium">
                  <Icon className="size-3.5" />
                  {t.label}
                </span>
              </button>
            );
          })}
        </div>
      </div>
    </section>
  );
}

function ThemePreview({ variant }: { variant: string }) {
  const bgClass =
    variant === 'light'
      ? 'bg-white'
      : variant === 'dark'
        ? 'bg-zinc-900'
        : 'bg-amber-50';
  const sidebarClass =
    variant === 'light'
      ? 'bg-gray-100'
      : variant === 'dark'
        ? 'bg-zinc-800'
        : 'bg-amber-100/80';
  const cardClass =
    variant === 'light'
      ? 'bg-gray-50'
      : variant === 'dark'
        ? 'bg-zinc-700/50'
        : 'bg-amber-100/50';
  const lineClass =
    variant === 'light'
      ? 'bg-gray-200'
      : variant === 'dark'
        ? 'bg-zinc-600'
        : 'bg-amber-200/80';
  const accentClass =
    variant === 'light'
      ? 'bg-blue-500'
      : variant === 'dark'
        ? 'bg-blue-500'
        : 'bg-amber-600';

  return (
    <div
      className={cn(
        'flex aspect-[16/10] w-full overflow-hidden rounded-md',
        bgClass,
      )}
    >
      {/* Sidebar mock */}
      <div className={cn('flex w-1/4 flex-col gap-1.5 p-1.5', sidebarClass)}>
        <div className={cn('h-1 w-3/4 rounded-full', lineClass)} />
        <div className={cn('h-1 w-1/2 rounded-full', lineClass)} />
        <div className={cn('h-1 w-2/3 rounded-full', lineClass)} />
        <div className={cn('h-1 w-1/2 rounded-full', lineClass)} />
      </div>
      {/* Main content mock */}
      <div className="flex flex-1 flex-col gap-1.5 p-2">
        <div className={cn('h-1 w-1/2 rounded-full', lineClass)} />
        <div className={cn('h-1 w-3/4 rounded-full', lineClass)} />
        <div className={cn('mt-auto flex h-4 items-center justify-center rounded-sm', cardClass)}>
          <div className={cn('h-1.5 w-8 rounded-full', accentClass)} />
        </div>
        <div className={cn('h-1 w-2/3 rounded-full', lineClass)} />
      </div>
    </div>
  );
}
