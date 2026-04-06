'use client';

import { createAuthProvider, AuthProvider as IAuthProvider, Session, User } from '@/lib/auth';
import { createContext, useContext, useEffect, useMemo, useState } from 'react';

interface AuthContextType {
  session: Session | null;
  user: User | null;
  loading: boolean;
  signIn: IAuthProvider['signIn'];
  signUp: IAuthProvider['signUp'];
  signOut: IAuthProvider['signOut'];
  signInWithOAuth: IAuthProvider['signInWithOAuth'];
  verifyOtp: IAuthProvider['verifyOtp'];
  resendOtp: IAuthProvider['resendOtp'];
}

const throwIfOutsideProvider = (): never => {
  throw new Error('useAuth must be used within AuthProvider');
};

const AuthContext = createContext<AuthContextType>({
  session: null,
  user: null,
  loading: true,
  signIn: throwIfOutsideProvider,
  signUp: throwIfOutsideProvider,
  signOut: throwIfOutsideProvider,
  signInWithOAuth: throwIfOutsideProvider,
  verifyOtp: throwIfOutsideProvider,
  resendOtp: throwIfOutsideProvider,
});

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const provider = useMemo(() => createAuthProvider(), []);
  const [isLoading, setIsLoading] = useState(true);
  const [session, setSession] = useState<Session | null>(null);

  useEffect(() => {
    const subscription = provider.onAuthStateChange((_, newSession) => {
      setIsLoading(false);
      if (!newSession) {
        setSession(null);
        return;
      }

      setSession(newSession);
    });

    return () => subscription.unsubscribe();
  }, [provider]);

  const value: AuthContextType = useMemo(
    () => ({
      session,
      user: session?.user ?? null,
      loading: isLoading,
      signIn: provider.signIn.bind(provider),
      signUp: provider.signUp.bind(provider),
      signOut: provider.signOut.bind(provider),
      signInWithOAuth: provider.signInWithOAuth.bind(provider),
      verifyOtp: provider.verifyOtp.bind(provider),
      resendOtp: provider.resendOtp.bind(provider),
    }),
    [session, isLoading, provider],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  return useContext(AuthContext);
}
