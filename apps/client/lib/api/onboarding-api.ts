import { OnboardingApi, Configuration } from '@/lib/types';
import { Session } from '@/lib/auth';

/**
 * Creates an OnboardingApi instance configured with session-based authentication.
 * Uses NEXT_PUBLIC_API_URL for base path (without /api suffix - generated paths include it).
 *
 * @param session - Supabase session with access_token
 * @returns Configured OnboardingApi instance
 * @throws Error if NEXT_PUBLIC_API_URL is not configured
 */
export function createOnboardingApi(session: Session): OnboardingApi {
  const basePath = process.env.NEXT_PUBLIC_API_URL;
  if (!basePath) {
    throw new Error('NEXT_PUBLIC_API_URL is not configured');
  }

  const config = new Configuration({
    basePath,
    accessToken: async () => session.access_token,
  });

  return new OnboardingApi(config);
}
