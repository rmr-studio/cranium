declare namespace NodeJS {
  interface ProcessEnv {
    NODE_ENV: 'development' | 'production' | 'test';

    /** Slack incoming webhook URL — server-only, never expose to client */
    SLACK_WEBHOOK_URL: string;

    /** Supabase project URL */
    NEXT_PUBLIC_SUPABASE_URL: string;
    /** Supabase anonymous/public key */
    NEXT_PUBLIC_SUPABASE_ANON_KEY: string;

    /** Canonical site URL for metadata/OG (e.g. https://getcranium.io) */
    NEXT_PUBLIC_SITE_URL?: string;
    /** URL to the client/dashboard app (e.g. http://localhost:3001) */
    NEXT_PUBLIC_CLIENT_URL?: string;

    /** Enable/disable auth features ("true" | "false") */
    NEXT_PUBLIC_AUTH_ENABLED?: string;
    /** Optional: shared cookie domain for cross-app auth */
    NEXT_PUBLIC_COOKIE_DOMAIN?: string;

    /** PostHog analytics key */
    NEXT_PUBLIC_POSTHOG_KEY?: string;
    /** PostHog API host */
    NEXT_PUBLIC_POSTHOG_HOST?: string;

    /** CDN URL for static assets */
    NEXT_PUBLIC_CDN_URL?: string;

    /** Google Tag Manager container ID (e.g. GTM-XXXXXXX) */
    NEXT_PUBLIC_GTM_ID?: string;
  }
}
