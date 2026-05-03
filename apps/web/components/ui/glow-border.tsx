import { cn } from '@/lib/utils';
import { FC, ReactNode } from 'react';

interface GlowBorderProps {
  children: ReactNode;
  className?: string;
  rounded?: 'sm' | 'md' | 'lg' | 'xl' | '2xl';
  opacity?: number;
}

export const GlowBorder: FC<GlowBorderProps> = ({
  children,
  className,
  rounded = '2xl',
  opacity = 40,
}) => {
  const roundedClass = {
    sm: 'rounded-sm',
    md: 'rounded-md',
    lg: 'rounded-lg',
    xl: 'rounded-xl',
    '2xl': 'rounded-2xl',
  }[rounded];

  return (
    <div className={cn('relative w-fit', className)}>
      <div
        className={cn(
          'absolute -inset-1 bg-gradient-to-r from-[#38bdf8]/30 via-[#8b5cf6]/30 to-[#f43f5e]/30 blur-md',
          roundedClass,
        )}
        style={{
          opacity: opacity / 100,
        }}
      />
      <div
        className={cn('relative', roundedClass)}
        style={{
          boxShadow: '0 0 12px 2px rgba(139, 92, 246, 0.1), 0 0 4px 1px rgba(139, 92, 246, 0.2)',
        }}
      >
        {children}
      </div>
    </div>
  );
};
