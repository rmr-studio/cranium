import { Session } from '@/lib/auth';
import { Configuration, IntegrationsApi } from '@/lib/types';

export function createIntegrationApi(session: Session): IntegrationsApi {
  const basePath = process.env.NEXT_PUBLIC_API_URL;
  if (!basePath) {
    throw new Error('NEXT_PUBLIC_API_URL is not configured');
  }

  const config = new Configuration({
    basePath,
    accessToken: async () => session.access_token,
  });

  return new IntegrationsApi(config);
}

export function createPublicIntegrationApi(): IntegrationsApi {
  const basePath = process.env.NEXT_PUBLIC_API_URL;
  if (!basePath) {
    throw new Error('NEXT_PUBLIC_API_URL is not configured');
  }

  const config = new Configuration({ basePath });
  return new IntegrationsApi(config);
}
