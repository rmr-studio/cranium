'use client';
import React, { useEffect, useState } from 'react';

import { cn } from '@/lib/utils';
import { motion } from 'motion/react';

type Direction = 'TOP' | 'LEFT' | 'BOTTOM' | 'RIGHT';

export function HoverBorderGradient({
  children,
  containerClassName,
  className,
  as: Tag = 'button',
  duration = 1,
  clockwise = true,
  ...props
}: React.PropsWithChildren<
  {
    as?: React.ElementType;
    containerClassName?: string;
    className?: string;
    duration?: number;
    clockwise?: boolean;
  } & React.HTMLAttributes<HTMLElement>
>) {
  const [hovered, setHovered] = useState<boolean>(false);
  const [direction, setDirection] = useState<Direction>('TOP');

  const rotateDirection = (currentDirection: Direction): Direction => {
    const directions: Direction[] = ['TOP', 'LEFT', 'BOTTOM', 'RIGHT'];
    const currentIndex = directions.indexOf(currentDirection);
    const nextIndex = clockwise
      ? (currentIndex - 1 + directions.length) % directions.length
      : (currentIndex + 1) % directions.length;
    return directions[nextIndex];
  };

  const movingMap: Record<Direction, string> = {
    TOP: 'radial-gradient(20.7% 50% at 50% 0%, #38bdf8 0%, rgba(56, 189, 248, 0) 100%)',
    LEFT: 'radial-gradient(16.6% 43.1% at 0% 50%, #f43f5e 0%, rgba(244, 63, 94, 0) 100%)',
    BOTTOM: 'radial-gradient(20.7% 50% at 50% 100%, #8b5cf6 0%, rgba(139, 92, 246, 0) 100%)',
    RIGHT: 'radial-gradient(16.2% 41.2% at 100% 50%, #f43f5e 0%, rgba(244, 63, 94, 0) 100%)',
  };

  const highlight =
    'radial-gradient(75% 181.16% at 50% 50%, #8b5cf6 0%, rgba(139, 92, 246, 0) 100%)';

  useEffect(() => {
    if (!hovered) {
      const interval = setInterval(() => {
        setDirection((prevState) => rotateDirection(prevState));
      }, duration * 1000);
      return () => clearInterval(interval);
    }
  }, [hovered]);
  return (
    <Tag
      onMouseEnter={(event: React.MouseEvent<HTMLDivElement>) => {
        setHovered(true);
      }}
      onMouseLeave={() => setHovered(false)}
      className={cn(
        'relative flex h-min w-fit flex-col flex-nowrap content-center items-center justify-center gap-10 overflow-visible rounded-xl border bg-black/20 decoration-clone p-[2px] transition duration-500 hover:bg-black/10 dark:bg-white/20',
        containerClassName,
      )}
      {...props}
    >
      <div className={cn('z-10 w-auto rounded-[inherit] bg-black px-4 py-2 text-white', className)}>
        {children}
      </div>
      <motion.div
        className={cn('absolute inset-0 z-0 flex-none overflow-hidden rounded-[inherit]')}
        style={{
          filter: 'blur(2px)',
          position: 'absolute',
          width: '100%',
          height: '100%',
        }}
        initial={{ background: movingMap[direction] }}
        animate={{
          background: hovered ? [movingMap[direction], highlight] : movingMap[direction],
        }}
        transition={{ ease: 'linear', duration: duration ?? 1 }}
      />
      <div className="absolute inset-[2px] z-1 flex-none rounded-[100px]" />
    </Tag>
  );
}
