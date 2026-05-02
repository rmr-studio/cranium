import { cn } from '@/lib/utils';
import React from 'react';
import { BGPattern, BGPatternProps } from './background/grids';

interface Props extends React.HTMLAttributes<HTMLDivElement> {
  id?: string;
  className?: string;
  variant?: BGPatternProps['variant'];
  mask?: BGPatternProps['mask'];
  size?: BGPatternProps['size'];
  fill?: BGPatternProps['fill'];
  gridClassName?: string;
  children?: React.ReactNode;
  navbarInverse?: boolean;
  /** Enable content-visibility: auto for below-fold render skipping */
  lazyRender?: boolean;
}

export const Section = React.forwardRef<HTMLDivElement, Props>(
  (
    {
      id,
      className,
      children,
      variant = 'grid',
      mask = 'fade-edges',
      gridClassName,
      size = 8,
      fill = 'color-mix(in srgb, var(--primary) 7.5%, transparent)',
      navbarInverse,
      lazyRender,
      ...rest
    },
    ref,
  ) => {
    return (
      <section
        id={id}
        ref={ref}
        className={cn('relative z-20 pt-16 md:pt-24 lg:px-12 lg:pt-20', className)}
        style={
          lazyRender ? { contentVisibility: 'auto', containIntrinsicSize: 'auto 800px' } : undefined
        }
        {...(navbarInverse ? { 'data-navbar-inverse': '' } : {})}
        {...rest}
      >
        <BGPattern
          variant={variant}
          mask={mask}
          className={cn(gridClassName)}
          size={size}
          style={{
            maskImage:
              'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
            maskComposite: 'intersect',
            WebkitMaskImage:
              'radial-gradient(ellipse at center, black 30%, transparent 75%), linear-gradient(to bottom, black 0%, black 40%, transparent 65%)',
            WebkitMaskComposite: 'source-in' as string,
          }}
          fill={fill}
        />
        {children}
      </section>
    );
  },
);

Section.displayName = 'Section';
