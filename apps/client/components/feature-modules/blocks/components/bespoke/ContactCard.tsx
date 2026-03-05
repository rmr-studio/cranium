import { Client } from '@/components/feature-modules/client/interface/client.interface';
import { Avatar, AvatarFallback, AvatarImage } from '@/components/ui/avatar';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@riven/ui/card';
import { getInitials } from '@riven/utils';
import Link from 'next/link';
import { FC } from 'react';
import { z } from 'zod';
import { RenderElementMetadata } from '../../util/block/block.registry';

const schema = z
  .object({
    client: z
      .object({
        id: z.string().optional(),
        name: z.string().optional(),
        email: z.string().optional(), // Support flat email
        phone: z.string().optional(), // Support flat phone
        contact: z
          .object({
            email: z.string().optional(),
            phone: z.string().optional(),
          })
          .partial()
          .optional(),
        company: z
          .union([
            z.string(), // Accept string
            z
              .object({
                name: z.string().optional(),
              })
              .partial(),
          ])
          .optional(),
        archived: z.boolean().optional(),
        type: z.string().optional(),
        workspaceId: z.string().optional(),
      })
      .passthrough()
      .optional(),
    accounts: z
      .array(
        z.object({
          entityId: z.string(),
          name: z.string().optional(),
          domain: z.string().optional(),
        }),
      )
      .optional(),
    href: z.string().optional(),
    avatarUrl: z.string().nullable().optional(),
    avatarShape: z.enum(['circle', 'square']).optional(),
    slots: z.record(z.any()).optional(),
  })
  .passthrough();

type Props = z.infer<typeof schema> & {
  client?: Pick<Client, 'id' | 'name' | 'contact' | 'company' | 'archived' | 'type'> & {
    workspaceId?: string;
  };
};

const Block: FC<Props> = ({ client, accounts, href, avatarUrl, avatarShape }) => {
  const name = client?.name ?? 'Unnamed client';
  const email = client?.contact?.email ?? client?.email ?? 'No email';
  const phone = client?.contact?.phone ?? client?.phone;
  const companyName = typeof client?.company === 'string' ? client.company : client?.company?.name;
  const account = accounts?.[0];

  const body = (
    <Card className="flex flex-col transition-shadow duration-150 hover:shadow-lg">
      <CardHeader className="flex flex-row items-center gap-4">
        <Avatar className={avatarShape === 'square' ? 'rounded-md' : 'rounded-full'}>
          <AvatarImage src={avatarUrl ?? undefined} alt={name} />
          <AvatarFallback>{getInitials(name)}</AvatarFallback>
        </Avatar>
        <div className="min-w-0 flex-1">
          <CardTitle className="truncate">{name}</CardTitle>
          <CardDescription className="truncate">{email}</CardDescription>
        </div>
      </CardHeader>
      <CardContent className="flex-1 space-y-3 text-sm text-muted-foreground">
        {phone ? <div className="font-medium text-foreground">{phone}</div> : null}
        {companyName ? (
          <div>
            <div className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              Company
            </div>
            <div className="text-foreground">{companyName}</div>
          </div>
        ) : null}
        {account ? (
          <div>
            <div className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              Linked Account
            </div>
            <div className="text-foreground">
              {account.name ?? account.domain ?? account.entityId}
            </div>
          </div>
        ) : null}
        {client?.type ? (
          <div>
            <div className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">
              Type
            </div>
            <div className="text-foreground">{client.type}</div>
          </div>
        ) : null}
      </CardContent>
    </Card>
  );

  if (href) {
    return (
      <Link href={href} className="block">
        {body}
      </Link>
    );
  }

  return <div className="block">{body}</div>;
};

export const ContactCard: RenderElementMetadata<typeof schema> = {
  type: 'CONTACT_CARD',
  name: 'Contact card',
  description: 'Displays primary client information with linked account summary.',
  schema,
  component: Block as FC<z.infer<typeof schema>>,
};
