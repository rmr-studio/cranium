import { BrandIcons, Integration } from '@/components/ui/diagrams/brand-icons';
import { cn } from '@/lib/utils';
import { Bookmark } from 'lucide-react';
import { motion, MotionValue, useMotionValue, useTransform } from 'motion/react';
import { FC } from 'react';

export interface FeatureCardProps {
  title: string;
  description: string;
  integrations?: Integration[];
  preview: React.ReactNode;
}

interface Props {
  card: FeatureCardProps;
  index: number;
  static?: boolean;
  total: number;
  progress?: MotionValue<number>;
}

export const FeatureCard: FC<Props> = ({
  card,
  index,
  total,
  progress,
  static: isStatic = false,
}) => {
  function computeCardState(p: number, index: number, total: number) {
    const DECK_EXTEND = 0.35; // how much further a deck card sits past pos=±1
    const DECK_SLOPE = 0.4; // how fast pos extends past ±1 per unit of localDist

    const easeOutCubic = (t: number) => 1 - Math.pow(1 - t, 3);

    const localDist = p * (total - 1) - index;
    const absL = Math.abs(localDist);
    const sign = localDist >= 0 ? 1 : -1;
    let pos: number;
    if (absL >= 1) {
      // Deck region: card sits behind the active peek at a slightly further offset.
      // Linear extension from ±1 to ±(1 + DECK_EXTEND) over [1, 1 + DECK_EXTEND/DECK_SLOPE].
      const extra = Math.min(DECK_EXTEND, (absL - 1) * DECK_SLOPE);
      pos = sign * (1 + extra);
    } else if (localDist >= 0) pos = easeOutCubic(localDist);
    else pos = easeOutCubic(1 + localDist) - 1;
    const mag = Math.abs(pos);
    // Visibility: fully visible for |l| up to 1.5 (peek + deck hint), fade out 1.5 → 2.6.
    const farFade = absL <= 1.5 ? 1 : absL >= 2.6 ? 0 : 1 - (absL - 1.5) / 1.1;
    return { pos, mag, farFade };
  }

  const MAX_TX = 700;
  const MAX_TY = 440;
  const MIN_SCALE = 0.68;
  const MIN_OPACITY = 0.7;

  const fallback = useMotionValue(0);
  const p = progress ?? fallback;

  const x = useTransform(p, (v) => -computeCardState(v, index, total).pos * MAX_TX);
  const y = useTransform(p, (v) => -computeCardState(v, index, total).pos * MAX_TY);
  const scale = useTransform(p, (v) => {
    const { mag } = computeCardState(v, index, total);
    return 1 - mag * (1 - MIN_SCALE);
  });
  const opacity = useTransform(p, (v) => {
    const { mag, farFade } = computeCardState(v, index, total);
    return farFade * (1 - mag * (1 - MIN_OPACITY));
  });
  const zIndex = useTransform(p, (v) => {
    const { mag, farFade } = computeCardState(v, index, total);
    return farFade < 0.5 ? -1 : Math.round(100 - mag * 60);
  });

  const { title, description, integrations, preview } = card;

  return (
    <motion.div
      style={isStatic ? undefined : { x, y, scale, opacity, zIndex }}
      className={cn(
        'flex items-stretch will-change-transform',
        isStatic
          ? 'relative aspect-[16/10] w-full'
          : 'absolute inset-0 m-auto mx-4 aspect-[3/5] sm:mx-auto sm:w-[min(60vw,1080px)] lg:aspect-[4/3] lg:w-[min(70vw,1080px)]',
      )}
    >
      <article className="flex h-full w-full flex-col overflow-hidden rounded-[20px] bg-zinc-950 text-zinc-200 shadow-2xl ring-1 shadow-black/50 ring-white/5">
        {/* Dark Instagram-style chrome */}
        <header className="flex items-center justify-end px-4 py-3">
          <div className="flex items-center gap-1">
            <span className="size-1 rounded-full bg-zinc-400" />
            <span className="size-1 rounded-full bg-zinc-400" />
            <span className="size-1 rounded-full bg-zinc-400" />
          </div>
        </header>

        {/* Light inner content panel */}
        <div className="mx-3 flex max-h-[calc(92%-theme(spacing.12))] flex-1 flex-col gap-5 rounded-[10px] bg-zinc-900 text-foreground">
          <div className="flex flex-col items-start justify-between gap-4 p-2 uppercase sm:p-6 md:flex-row md:p-8">
            <div className="flex items-center gap-1.5">
              <div className="mr-2 h-1.5 w-1.5 rounded-full bg-green-500" />
              <span className="font-bit text-base tracking-normal text-zinc-100">{title}</span>
            </div>
            <span className="max-w-none font-display text-xs leading-none tracking-tight text-zinc-200 normal-case md:max-w-[60%] md:text-right md:text-sm">
              {description}
            </span>
          </div>
          <div className="overflow-hidden">{preview}</div>
        </div>

        <footer className="flex items-center justify-between p-4 text-zinc-300/90">
          {integrations && integrations.length > 0 ? (
            <div className="flex w-fit items-center rounded-lg border border-primary/30 bg-zinc-900 px-2.5 py-1.5 font-display text-[clamp(0.60rem,3vw,0.875rem)] tracking-tight whitespace-nowrap text-content/80 sm:text-base">
              <div className="mr-2 h-1.5 w-1.5 rounded-full bg-green-500" />
              <span className="mr-2 text-zinc-100">Integrated with</span>
              {integrations.map((brand) => {
                const BrandIcon = BrandIcons[brand];
                return (
                  <span key={brand} className="mr-2 overflow-hidden rounded-full">
                    <BrandIcon size={28} />
                  </span>
                );
              })}
            </div>
          ) : (
            <div />
          )}
          <div className="hidden items-center gap-3 lg:flex">
            <span className="font-mono text-[10px] tracking-[0.18em] text-zinc-500 uppercase tabular-nums">
              {String(index + 1).padStart(2, '0')} / {String(total).padStart(2, '0')}
            </span>
            <Bookmark className="h-5 w-5" strokeWidth={1.5} />
          </div>
        </footer>
      </article>
    </motion.div>
  );
};
