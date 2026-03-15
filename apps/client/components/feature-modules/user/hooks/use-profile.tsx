'use client';

import { fetchSessionUser } from '@/components/feature-modules/user/service/user.service';
import { useAuth } from '@/components/provider/auth-context';
import { fromError, isResponseError } from '@/lib/util/error/error.util';
import { useQuery } from '@tanstack/react-query';

export function useProfile() {
  const { session, loading } = useAuth();

  const query = useQuery({
    queryKey: ['userProfile', session?.user.id],
    queryFn: () => {
      if (!session?.user.id) {
        throw fromError({
          message: 'No active session found',
          status: 401,
          error: 'NO_SESSION',
        });
      }
      return fetchSessionUser(session);
    },
    enabled: !!session?.user.id, // Only fetch if user is authenticated
    retry: (count, error) => {
      // Retry once on failure, but not on network errors
      if (isResponseError(error)) return false;

      return count < 2;
    }, // Retry once on failure
    staleTime: 5 * 60 * 1000, // Cache for 5 minutes,
  });

  return {
    ...query,
    isLoadingAuth: loading,
  };
}
