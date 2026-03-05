import { Button } from '@riven/ui/button';
import { cn } from '@riven/utils';
import Link from 'next/link';
import { FC } from 'react';
import { z } from 'zod';
import { createRenderElement } from '../../util/render/render-element.registry';

type Props = z.infer<typeof BlockButtonSchema>;

const BlockButtonSchema = z
  .object({
    label: z.string().optional(),
    href: z.string().optional(),
    className: z.string().optional(),
    variant: z.enum(['default', 'secondary', 'destructive', 'ghost', 'outline']).optional(),
    // Map to buttonVariants size
    size: z.enum(['default', 'sm', 'lg', 'icon']).optional().nullable(),
    icon: z.any().optional(),
  })
  .passthrough();

const Block: FC<Props> = ({ label = 'Button', icon, href, className, ...rest }) => {
  const inner = (
    <>
      {icon ? <span className="mr-2 inline-flex items-center">{icon}</span> : null}
      {label}
    </>
  );
  if (href) {
    return (
      <Button className={cn('w-full justify-center', className)} {...rest} asChild>
        <Link href={href}>{inner}</Link>
      </Button>
    );
  }
  return (
    <Button className={cn('w-full justify-center', className)} {...rest}>
      {inner}
    </Button>
  );
};

export const ButtonBlock = createRenderElement({
  type: 'BUTTON',
  name: 'Button',
  description: 'Action button that can link to another view.',
  schema: BlockButtonSchema,
  component: Block,
});
