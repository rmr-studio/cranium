declare namespace NodeJS {
  interface ProcessEnv {
    NODE_ENV: 'development' | 'production' | 'test';

    /** Supabase project URL */
    NEXT_PUBLIC_SUPABASE_URL: string;
    /** Supabase anonymous/public key */
    NEXT_PUBLIC_SUPABASE_ANON_KEY: string;

    /** Backend API base URL (e.g. http://localhost:8081) */
    NEXT_PUBLIC_API_URL: string;
    /** Frontend URL for OAuth redirects (e.g. http://localhost:3001) */
    NEXT_PUBLIC_HOSTED_URL: string;
    /** Auth provider type (e.g. "supabase") */
    NEXT_PUBLIC_AUTH_PROVIDER: string;

    /** Optional: shared cookie domain for cross-app auth */
    NEXT_PUBLIC_COOKIE_DOMAIN?: string;
    /** Optional: Google Maps API key for location features */
    NEXT_PUBLIC_GOOGLE_MAPS_API_KEY?: string;
  }
}
