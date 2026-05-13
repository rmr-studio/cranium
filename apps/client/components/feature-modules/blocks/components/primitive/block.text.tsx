import { cn } from '@cranium/utils';
import { FC, JSX } from 'react';
import { z } from 'zod';
import { createRenderElement } from '../../util/render/render-element.registry';

interface Props {
  text?: string;
  variant?: 'title' | 'subtitle' | 'body' | 'muted';
  align?: 'left' | 'center' | 'right';
  className?: string;
  placeholder?: string;
}

const Schema = z
  .object({
    text: z.string().optional(),
    variant: z.enum(['title', 'subtitle', 'body', 'muted']).optional(),
    align: z.enum(['left', 'center', 'right']).optional(),
    className: z.string().optional(),
    placeholder: z.string().optional(),
  })
  .passthrough();

const variantClasses: Record<NonNullable<Props['variant']>, string> = {
  title: 'text-2xl font-semibold text-foreground',
  subtitle: 'text-lg font-medium text-foreground',
  body: 'text-base text-foreground',
  muted: 'text-sm text-muted-foreground',
};

const alignClasses: Record<NonNullable<Props['align']>, string> = {
  left: 'text-left',
  center: 'text-center',
  right: 'text-right',
};

const tagMap: Record<NonNullable<Props['variant']>, string> = {
  title: 'h3',
  subtitle: 'h4',
  body: 'p',
  muted: 'p',
};

const Block: FC<Props> = ({ text, variant = 'body', align = 'left', className, placeholder }) => {
  // If no text and no placeholder, render nothing
  if (!text && !placeholder) return null;

  const Tag = tagMap[variant] as keyof JSX.IntrinsicElements;
  const displayText = text || placeholder;
  const isPlaceholder = !text && placeholder;

  return (
    <Tag
      className={cn(
        variantClasses[variant],
        alignClasses[align],
        isPlaceholder && 'text-muted-foreground/50 italic',
        className,
      )}
    >
      {displayText}
    </Tag>
  );
};

export const TextBlock = createRenderElement({
  type: 'TEXT',
  name: 'Text block',
  description: 'Simple text display with styling options.',
  schema: Schema,
  component: Block,
});
