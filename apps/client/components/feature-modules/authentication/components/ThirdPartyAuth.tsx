'use client';

import { Button } from '@riven/ui/button';
import type { ClassNameProps } from '@riven/utils';
import { OAuthProvider } from '@/lib/auth';
import { cn } from '@riven/utils';
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
        <div className="flex h-[2px] flex-grow rounded-lg bg-foreground"></div>
        <div className="px-4">{'Or continue with'}</div>
        <div className="flex h-[2px] flex-grow rounded-lg bg-foreground"></div>
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
