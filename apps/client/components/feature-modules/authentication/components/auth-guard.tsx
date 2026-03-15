'use client';

import { useProfile } from '@/components/feature-modules/user/hooks/use-profile';
import { useAuth } from '@/components/provider/auth-context';
import { FCWC } from '@/lib/interfaces/interface';
import { AnimatePresence, motion } from 'motion/react';
import { useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AppSplash } from '@/components/feature-modules/authentication/components/app-splash';

export const AuthGuard: FCWC = ({ children }) => {
  const { session, loading: authLoading } = useAuth();
  const { data: user, isLoading: profileLoading, isLoadingAuth, isError } = useProfile();
  const router = useRouter();
  const [ready, setReady] = useState(false);

  const isLoading = authLoading || isLoadingAuth || profileLoading;

  useEffect(() => {
    if (!authLoading && !session) {
      router.replace('/auth/login');
    }
  }, [session, authLoading, router]);

  useEffect(() => {
    if (!isLoading && isError && session) {
      router.replace('/auth/login');
    }
  }, [isLoading, isError, session, router]);

  useEffect(() => {
    if (!isLoading && session && user) {
      const timeout = setTimeout(() => setReady(true), 400);
      return () => clearTimeout(timeout);
    }
  }, [isLoading, session, user]);

  return (
    <>
      <AnimatePresence>{!ready && <AppSplash />}</AnimatePresence>

      {ready && (
        <motion.div
          initial={{ opacity: 0 }}
          animate={{ opacity: 1 }}
          transition={{ duration: 0.35, ease: [0.4, 0, 0.2, 1] }}
        >
          {children}
        </motion.div>
      )}
    </>
  );
};
