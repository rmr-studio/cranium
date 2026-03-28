import { cn } from '@/lib/utils';
import { FC } from 'react';
import { Button } from './button';

interface Props {
  children: React.ReactNode;
  className?: string;
  size?: keyof typeof sizeStyles;
}

const sizeStyles = {
  sm: 'h-7 gap-1 px-2.5 py-0.5 text-xs md:h-8 md:gap-1.5 md:px-3 md:text-xs',
  xl: 'px-8 py-4 h-auto gap-2 text-sm md:gap-3  md:text-lg font-semibold ',
  default: 'h-9 gap-1.5 px-4 py-1 text-sm md:h-10 md:gap-2 md:px-5 md:text-sm',
};

// Gradient colors adapt per theme via --cta-g1/g2/g3/muted CSS vars
const glowGradient =
  'conic-gradient(from var(--border-angle), var(--cta-g1), var(--cta-g2), var(--cta-g3), var(--cta-g2), var(--cta-g1))';

const borderGradient =
  'linear-gradient(var(--background), var(--background)) padding-box, conic-gradient(from var(--border-angle), var(--cta-g1), var(--cta-g2) var(--arc-start), var(--cta-muted) calc(var(--arc-start) + 3%), var(--cta-muted) calc(97% - var(--arc-start)), var(--cta-g3) calc(100% - var(--arc-start)), var(--cta-g1)) border-box';

export const CtaButton: FC<Props> = ({ children, className, size = 'default' }) => {
  return (
    <div className="group relative w-fit">
      {/* Glow — blurred gradient behind, fades in on hover */}
      <div
        className="absolute -inset-1 animate-[border-rotate_4s_linear_infinite] rounded-2xl opacity-0 blur-md transition-opacity duration-700 ease-out group-hover:opacity-40"
        style={{ background: glowGradient }}
      />
      {/* Border wrapper — arc expands from small segment to full border on hover */}
      <div
        className="relative animate-[border-rotate_4s_linear_infinite] rounded-xl border-3 border-transparent [--arc-start:7%] group-hover:[--arc-start:48%]"
        style={{
          background: borderGradient,
          transition: '--arc-start 0.6s ease-out',
        }}
      >
        <Button
          className={cn(
            'cursor-pointer items-center rounded-lg border-0 bg-foreground/80 font-mono tracking-wide text-background outline-0 transition-colors hover:bg-foreground/85',
            sizeStyles[size],
            className,
          )}
        >
          {children}
        </Button>
      </div>
    </div>
  );
};
