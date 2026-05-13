'use client';

import { Button } from '@cranium/ui/button';
import type { ClassNameProps } from '@cranium/utils';
import { OAuthProvider } from '@/lib/auth';
import { cn } from '@cranium/utils';
import React from 'react';
import { FaGoogle } from 'react-icons/fa';

interface ThirdPartyProps extends ClassNameProps {
  iconClass?: string;
  text?: string;
  socialProviderAuthentication: (provider: OAuthProvider) => Promise<void>;
}

const ThirdParty: React.FC<ThirdPartyProps> = ({
  className,
  iconClass,
  socialProviderAuthentication,
}) => {
  const handleAuth = async (provider: OAuthProvider) => {
    try {
      await socialProviderAuthentication(provider);
    } catch (error) {
      console.error('Authentication failed:', error);
    }
  };

  return (
    <>
      <div className={cn('flex h-fit w-full items-center', className)}>
        <div className="h-px flex-grow bg-foreground/15"></div>
        <div className="px-4 text-sm text-muted-foreground">{'Or continue with'}</div>
        <div className="h-px flex-grow bg-foreground/15"></div>
      </div>
      <section className="space-y-2">
        <Button
          onClick={async () => await handleAuth(OAuthProvider.Google)}
          variant={'outline'}
          className="relative w-full"
        >
          <FaGoogle className={cn('text-base', iconClass)} />
          <span className="ml-2">Google</span>
        </Button>
      </section>
    </>
  );
};

export default ThirdParty;
