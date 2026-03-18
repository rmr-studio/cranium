'use client';

import { fetchSessionUser } from '@/components/feature-modules/user/service/user.service';
import { useAuth } from '@/components/provider/auth-context';
import { bustImageCache } from '@/lib/util/avatar/bust-image-cache';
import { fromError, isResponseError } from '@/lib/util/error/error.util';
import { useQuery } from '@tanstack/react-query';

export function useProfile() {
  const { session, loading } = useAuth();

  const query = useQuery({
    queryKey: ['userProfile', session?.user.id],
    queryFn: async () => {
      if (!session?.user.id) {
        throw fromError({
          message: 'No active session found',
          status: 401,
          error: 'NO_SESSION',
        });
      }
      const user = await fetchSessionUser(session);

      // Avatar URLs are static paths (e.g. /api/v1/avatars/user/{id}) that
      // don't change when a new image is uploaded. Append a fetch-time
      // timestamp so the browser doesn't serve a stale cached image.
      const v = Date.now();
      return {
        ...user,
        avatarUrl: bustImageCache(user.avatarUrl, v),
        memberships: user.memberships.map((m) => ({
          ...m,
          workspace: {
            ...m.workspace,
            avatarUrl: bustImageCache(m.workspace.avatarUrl, v),
          },
        })),
        defaultWorkspace: user.defaultWorkspace
          ? { ...user.defaultWorkspace, avatarUrl: bustImageCache(user.defaultWorkspace.avatarUrl, v) }
          : user.defaultWorkspace,
      };
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
