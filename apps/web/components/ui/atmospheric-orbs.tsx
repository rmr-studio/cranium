import { cn } from '@/lib/utils';

interface Orb {
  color: string;
  size: number;
  opacity: number;
  blur: number;
  position: { top?: string; bottom?: string; left?: string; right?: string };
  translate?: string;
}

const BLUE: Orb = {
  color: 'oklch(0.18 0.06 240)',
  size: 600,
  opacity: 60,
  blur: 150,
  position: { top: '10%', left: '15%' },
  translate: '-50%, -50%',
};

const ROSE: Orb = {
  color: 'oklch(0.22 0.09 350)',
  size: 500,
  opacity: 25,
  blur: 130,
  position: { bottom: '15%', right: '10%' },
  translate: '50%, 50%',
};

const ACCENT: Orb = {
  color: 'oklch(0.22 0.09 350)',
  size: 350,
  opacity: 15,
  blur: 100,
  position: { top: '55%', left: '50%' },
};

const presets: Record<string, Orb[]> = {
  /** Blue-dominant — default outer atmosphere */
  outer: [BLUE, ROSE, ACCENT],
  /** Rose-dominant — inner card atmosphere (colors swapped) */
  inner: [
    { ...ROSE, size: 600, opacity: 40, blur: 150, position: { top: '10%', left: '15%' }, translate: '-50%, -50%' },
    { ...BLUE, size: 500, opacity: 25, blur: 130, position: { bottom: '15%', right: '10%' }, translate: '50%, 50%' },
    { color: 'oklch(0.2 0.07 200)', size: 350, opacity: 15, blur: 100, position: { top: '55%', left: '50%' } },
  ],
};

interface AtmosphericOrbsProps {
  /** Preset orb arrangement, or pass custom orbs */
  variant?: string;
  orbs?: Orb[];
  /** Wrapper opacity (e.g. 60 for the inner card variant) */
  opacity?: number;
  className?: string;
}

export function AtmosphericOrbs({ variant = 'outer', orbs, opacity, className }: AtmosphericOrbsProps) {
  const items = orbs || presets[variant];

  return (
    <div
      className={cn('pointer-events-none absolute inset-0 overflow-hidden', opacity && `opacity-${opacity}`, className)}
      aria-hidden="true"
    >
      {items.map((orb, i) => (
        <div
          key={i}
          className={`absolute rounded-full`}
          style={{
            background: orb.color,
            width: orb.size,
            height: orb.size,
            opacity: orb.opacity / 100,
            filter: `blur(${orb.blur}px)`,
            ...orb.position,
            ...(orb.translate ? { transform: `translate(${orb.translate})` } : {}),
          }}
        />
      ))}
    </div>
  );
}

export type { Orb };
