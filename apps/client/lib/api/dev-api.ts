import { DevApi, Configuration } from '@/lib/types';
import { Session } from '@/lib/auth';

export function createDevApi(session: Session): DevApi {
  const basePath = process.env.NEXT_PUBLIC_API_URL;
  if (!basePath) {
    throw new Error('NEXT_PUBLIC_API_URL is not configured');
  }

  const config = new Configuration({
    basePath,
    accessToken: async () => session.access_token,
  });

  return new DevApi(config);
}
