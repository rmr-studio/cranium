import { z } from 'zod';

const envSchema = z
  .object({
    NODE_ENV: z.enum(['development', 'production', 'test']).default('development'),

    // ── Server-only vars ──
    SLACK_WEBHOOK_URL: z.string().url('SLACK_WEBHOOK_URL must be a valid URL'),

    NEXT_PUBLIC_AUTH_ENABLED: z.string().optional(),
    NEXT_PUBLIC_SUPABASE_URL: z.string().url({ message: 'NEXT_PUBLIC_SUPABASE_URL is required' }),
    NEXT_PUBLIC_SUPABASE_ANON_KEY: z.string().min(1, 'NEXT_PUBLIC_SUPABASE_ANON_KEY is required'),
    NEXT_PUBLIC_COOKIE_DOMAIN: z.string().optional(),

    NEXT_PUBLIC_SITE_URL: z.string().url().optional(),
    NEXT_PUBLIC_CLIENT_URL: z.string().optional(),

    NEXT_PUBLIC_CDN_URL: z.string().url().optional(),

    NEXT_PUBLIC_POSTHOG_KEY: z.string().optional(),
    NEXT_PUBLIC_POSTHOG_HOST: z.string().url().optional(),
  })
  .superRefine((val, ctx) => {
    const hasPosthogKey = !!val.NEXT_PUBLIC_POSTHOG_KEY;
    const hasPosthogHost = !!val.NEXT_PUBLIC_POSTHOG_HOST;
    if (hasPosthogKey && !hasPosthogHost) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'NEXT_PUBLIC_POSTHOG_HOST is required when NEXT_PUBLIC_POSTHOG_KEY is set',
        path: ['NEXT_PUBLIC_POSTHOG_HOST'],
      });
    }
    if (hasPosthogHost && !hasPosthogKey) {
      ctx.addIssue({
        code: z.ZodIssueCode.custom,
        message: 'NEXT_PUBLIC_POSTHOG_KEY is required when NEXT_PUBLIC_POSTHOG_HOST is set',
        path: ['NEXT_PUBLIC_POSTHOG_KEY'],
      });
    }
  });

export function validateEnv() {
  const result = envSchema.safeParse(process.env);

  if (!result.success) {
    const errors = result.error.issues
      .map((issue) => `  - ${issue.path.join('.')}: ${issue.message}`)
      .join('\n');

    throw new Error(`\n\nEnvironment validation failed:\n${errors}\n`);
  }

  return result.data;
}
