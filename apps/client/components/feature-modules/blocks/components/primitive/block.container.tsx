import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@cranium/ui/card';
import { cn } from '@cranium/utils';
import { FC, ReactNode, useEffect, useRef } from 'react';
import { z } from 'zod';
import { RenderElementMetadata } from '../../util/block/block.registry';

interface Props {
  title?: string;
  description?: string;
  variant?: 'card' | 'plain';
  padded?: boolean;
  className?: string;
  children?: ReactNode;
}

const Schema = z
  .object({
    title: z.string().optional(),
    description: z.string().optional(),
    variant: z.enum(['card', 'plain']).optional(),
    padded: z.boolean().optional(),
    className: z.string().optional(),
  })
  .passthrough();

export const Block: FC<Props> = ({
  title,
  description,
  variant = 'card',
  padded = true,
  className,
  children,
}) => {
  const hostRef = useRef<HTMLDivElement>(null);
  const observerRef = useRef<MutationObserver | null>(null);

  useEffect(() => {
    const host = hostRef.current;
    if (!host) {
      console.warn('Grid host not found for LayoutContainerBlock');
      return;
    }

    const gridItem = host.closest('.grid-stack-item');
    if (!gridItem) {
      console.warn('Grid item not found for LayoutContainerBlock');
      return;
    }

    const moveSubGrid = () => {
      try {
        const subGrid = gridItem.querySelector('.grid-stack-subgrid') as HTMLElement | null;
        if (subGrid && !host.contains(subGrid)) {
          host.appendChild(subGrid);
        }
      } catch (error) {
        // Catch any errors when moving subgrid during DOM mutations
        console.warn('SubGrid move error (non-critical):', error);
      }
    };

    moveSubGrid();
    observerRef.current = new MutationObserver(moveSubGrid);
    const observer = observerRef.current;
    observer.observe(gridItem, { childList: true, subtree: true, attributes: false });

    return () => {
      observer.disconnect();
      observerRef.current = null;
    };
  }, []);

  if (variant === 'plain') {
    return (
      <div className={cn('flex w-full flex-col', padded && 'p-4', className)}>
        {title || description ? (
          <div className="mb-4 space-y-1">
            {title ? <h3 className="text-base font-semibold">{title}</h3> : null}
            {description ? <p className="text-sm text-muted-foreground">{description}</p> : null}
          </div>
        ) : null}
        <div className="w-full flex-1">
          <div ref={hostRef} data-grid-host className="min-h-12 w-full" />
          {children ? <div className="mt-4">{children}</div> : null}
        </div>
      </div>
    );
  }

  return (
    <Card
      className={cn(
        'm-0 flex w-full flex-col gap-y-0 border-0 p-0 shadow-none transition-shadow duration-150',
        className,
      )}
    >
      {(title || description) && (
        <CardHeader>
          {title ? <CardTitle className="text-base font-semibold">{title}</CardTitle> : null}
          {description ? <CardDescription>{description}</CardDescription> : null}
        </CardHeader>
      )}
      <CardContent className={cn('flex-1', !padded && 'px-2 pb-2')}>
        <div ref={hostRef} data-grid-host className="min-h-12 w-full" />
        {children ? <div className="mt-4">{children}</div> : null}
      </CardContent>
    </Card>
  );
};

export const LayoutContainerBlock: RenderElementMetadata<typeof Schema> = {
  type: 'LAYOUT_CONTAINER',
  name: 'Layout container',
  description: 'Wrapper component that hosts a nested grid layout.',
  schema: Schema,
  component: Block,
};
