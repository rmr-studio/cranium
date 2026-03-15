/**
 * bg-blue-500 bg-emerald-500 bg-violet-500 bg-rose-500 bg-amber-500 bg-cyan-500
 * ^ Tailwind safelist — keeps JIT from purging dynamically-applied palette classes
 */
const PALETTE_COLORS = [
  'bg-blue-500',
  'bg-emerald-500',
  'bg-violet-500',
  'bg-rose-500',
  'bg-amber-500',
  'bg-cyan-500',
];

export function getInitials(name: string): string {
  const parts = name.trim().split(/\s+/);
  const first = parts[0]?.[0] ?? '';
  const second = parts[1]?.[0] ?? '';
  return (first + second).toUpperCase();
}

export function getPaletteColor(name: string): string {
  let hash = 0;
  for (let i = 0; i < name.length; i++) {
    hash = (hash * 31 + name.charCodeAt(i)) & 0xffffffff;
  }
  return PALETTE_COLORS[Math.abs(hash) % PALETTE_COLORS.length] ?? 'bg-blue-500';
}
