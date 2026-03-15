'use client';

import { useAuth } from '@/components/provider/auth-context';
import { useRouter } from 'next/navigation';
import { useEffect } from 'react';
import { AppSplash } from '@/components/feature-modules/authentication/components/app-splash';

export const RootRedirect = () => {
  const { session, loading } = useAuth();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;

    if (!session) {
      router.replace('/auth/login');
      return;
    }

    const savedWorkspace = localStorage.getItem('selectedWorkspace');
    if (savedWorkspace) {
      router.replace(`/dashboard/workspace/${savedWorkspace}`);
    } else {
      router.replace('/dashboard/workspace');
    }
  }, [session, loading, router]);

  return <AppSplash />;
};
