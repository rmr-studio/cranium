import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@riven/ui/card';
import { Address } from '@/lib/interfaces/common.interface';
import { FC, ReactNode } from 'react';
import { z } from 'zod';
import { RenderElementMetadata } from '../../util/block/block.registry';

interface Props {
  address?: Partial<Address>;
  title?: string;
  description?: string;
  footer?: ReactNode;
  slots?: Record<string, ReactNode>;
}

const schema = z
  .object({
    address: z
      .object({
        street: z.string().optional(),
        city: z.string().optional(),
        state: z.string().optional(),
        postalCode: z.string().optional(),
        country: z.string().optional(),
      })
      .partial()
      .optional(),
    title: z.string().optional(),
    description: z.string().optional(),
    footer: z.any().optional(),
    slots: z.record(z.any()).optional(),
  })
  .passthrough();

const block: FC<Props> = ({ address, title = 'Address', description, footer }) => {
  const { street, city, state, postalCode, country } = address ?? {};
  const location = [city, state, postalCode].filter(Boolean).join(', ');

  return (
    <Card className="flex flex-col transition-shadow duration-150 hover:shadow-lg">
      <CardHeader>
        <CardTitle className="text-base font-semibold">{title}</CardTitle>
        {description ? <CardDescription>{description}</CardDescription> : null}
      </CardHeader>
      <CardContent className="flex-1 space-y-1 text-sm text-foreground">
        {street ? <div>{street}</div> : null}
        {location ? <div>{location}</div> : null}
        {country ? <div>{country}</div> : null}
      </CardContent>
      {footer ? <CardFooter className="text-sm text-muted-foreground">{footer}</CardFooter> : null}
    </Card>
  );
};

export const AddressCard: RenderElementMetadata<typeof schema> = {
  type: 'ADDRESS_CARD',
  name: 'Address card',
  description: 'Shows postal address details in a compact card.',
  schema: schema,
  component: block,
};
