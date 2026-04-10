'use client';

import { cn } from '@/lib/utils';
import { motion } from 'motion/react';
import * as React from 'react';

type StarLayerProps = React.ComponentProps<'div'> & {
  count: number;
  size: number;
  duration: number;
  starColor: string;
};

function generateStars(count: number, starColor: string) {
  const shadows: string[] = [];
  for (let i = 0; i < count; i++) {
    const x = Math.floor(Math.random() * 4000) - 2000;
    const y = Math.floor(Math.random() * 4000) - 2000;
    shadows.push(`${x}px ${y}px ${starColor}`);
  }
  return shadows.join(', ');
}

function StarLayer({
  count = 1000,
  size = 1,
  duration = 50,
  starColor = '#fff',
  className,
  ...props
}: StarLayerProps) {
  const [boxShadow, setBoxShadow] = React.useState<string>('');

  React.useEffect(() => {
    setBoxShadow(generateStars(count, starColor));
  }, [count, starColor]);

  return (
    <div
      data-slot="star-layer"
      className={cn('animate-stars absolute top-0 left-0 h-[2000px] w-full', className)}
      style={{ animationDuration: `${duration}s` }}
      {...props}
    >
      <div
        className="absolute rounded-full bg-transparent"
        style={{
          width: `${size}px`,
          height: `${size}px`,
          boxShadow: boxShadow,
        }}
      />
      <div
        className="absolute top-[2000px] rounded-full bg-transparent"
        style={{
          width: `${size}px`,
          height: `${size}px`,
          boxShadow: boxShadow,
        }}
      />
    </div>
  );
}

type StarsBackgroundProps = React.ComponentProps<'div'> & {
  factor?: number;
  speed?: number;
  starColor?: string;
  pointerEvents?: boolean;
};

function StarsBackground({
  children,
  className,
  factor = 0.05,
  speed = 50,
  starColor = '#fff',
  pointerEvents = true,
  ...props
}: StarsBackgroundProps) {
  const wrapperRef = React.useRef<HTMLDivElement>(null);

  const handleMouseMove = React.useCallback(
    (e: React.MouseEvent<HTMLDivElement, MouseEvent>) => {
      const el = wrapperRef.current;
      if (!el) return;
      const centerX = window.innerWidth / 2;
      const centerY = window.innerHeight / 2;
      const x = -(e.clientX - centerX) * factor;
      const y = -(e.clientY - centerY) * factor;
      el.style.transform = `translate(${x}px, ${y}px)`;
    },
    [factor],
  );

  return (
    <div
      data-slot="stars-background"
      className={cn('relative size-full overflow-hidden', className)}
      onMouseMove={handleMouseMove}
      {...props}
    >
      <motion.div
        initial={{ opacity: 0 }}
        animate={{ opacity: 1 }}
        ref={wrapperRef}
        className={cn('transition-transform duration-300 ease-out', {
          'pointer-events-none': !pointerEvents,
        })}
      >
        <StarLayer count={1000} size={1} duration={speed} starColor={starColor} />
        <StarLayer count={400} size={2} duration={speed * 2} starColor={starColor} />
        <StarLayer count={200} size={3} duration={speed * 3} starColor={starColor} />
      </motion.div>
      {children}
    </div>
  );
}

export { StarLayer, StarsBackground, type StarLayerProps, type StarsBackgroundProps };
