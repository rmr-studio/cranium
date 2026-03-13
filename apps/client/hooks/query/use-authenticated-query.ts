import { useAuth } from '@/components/provider/auth-context';
import { AuthenticatedQueryResult } from '@/lib/interfaces/interface';
import {
  QueryKey,
  useQuery,
  UseQueryOptions,
} from '@tanstack/react-query';

/**
 * Wrapper around useQuery that handles auth-gating automatically.
 *
 * - Disables the query when session is null or auth is loading
 * - Merges the caller's `enabled` condition with auth checks
 * - Returns `isLoadingAuth` flag alongside standard query result
 */
export function useAuthenticatedQuery<
  TQueryFnData = unknown,
  TError = Error,
  TData = TQueryFnData,
  TQueryKey extends QueryKey = QueryKey,
>(
  options: UseQueryOptions<TQueryFnData, TError, TData, TQueryKey>,
): AuthenticatedQueryResult<TData, TError> {
  const { session, loading } = useAuth();

  const isAuthReady = !!session && !loading;
  const callerEnabled = options.enabled ?? true;

  const query = useQuery<TQueryFnData, TError, TData, TQueryKey>({
    ...options,
    enabled: isAuthReady && callerEnabled,
  });

  return {
    ...query,
    isLoadingAuth: loading,
  };
}
