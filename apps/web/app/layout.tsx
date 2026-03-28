import { Footer } from '@/components/footer';
import { Navbar } from '@/components/navbar';
import { PageStage } from '@/components/page-stage';
import { BGPattern } from '@/components/ui/background/grids';
import { getCdnUrl } from '@/lib/cdn-image-loader';
import { AuthProvider } from '@/providers/auth-provider';
import { LazyQueryProvider as QueryProvider } from '@/providers/lazy-query-provider';

import { ThemeProvider } from '@/providers/theme-provider';
import type { Metadata, Viewport } from 'next';
import { Geist, Geist_Mono, Instrument_Serif, Space_Mono } from 'next/font/google';
import { Toaster } from 'sonner';
import './globals.css';

const geistSans = Geist({
  variable: '--font-geist-sans',
  subsets: ['latin'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

const instrumentSerif = Instrument_Serif({
  variable: '--font-instrument-serif',
  subsets: ['latin'],
  weight: '400',
  style: ['normal', 'italic'],
});

const spaceMono = Space_Mono({
  variable: '--font-space-mono',
  subsets: ['latin'],
  weight: ['400', '700'],
});

const SITE_TITLE = 'Riven — Customer Lifecycle Intelligence for Growing Teams';
const SITE_DESCRIPTION =
  'One workspace for your customer lifecycle stack. From marketing to CRMs to payments, analytics and support. Cross-domain intelligence surfaces churn risks, hidden patterns, and growth opportunities no single tool can see. Query across every platform in plain English, tag and track accounts, and act on insights without leaving the tab.';

const ogImage = process.env.NEXT_PUBLIC_CDN_URL
  ? `${process.env.NEXT_PUBLIC_CDN_URL}/images/og-image.png`
  : '/og-image.png';

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || 'https://getriven.io'),

  title: {
    default: SITE_TITLE,
    template: '%s | Riven',
  },

  description: SITE_DESCRIPTION,

  keywords: [
    'customer lifecycle intelligence',
    'cross-domain intelligence',
    'churn analysis',
    'churn retrospective',
    'unified customer data',
    'SaaS analytics',
    'customer retention platform',
    'AI business intelligence',
    'Stripe CRM integration',
    'support data analytics',
    'plain English data queries',
    'operational intelligence',
    'B2C SaaS tools',
    'DTC analytics',
  ],

  openGraph: {
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
    url: 'https://getriven.io',
    siteName: 'Riven',
    locale: 'en_US',
    type: 'website',
    images: [
      {
        url: ogImage,
        width: 1200,
        height: 630,
        alt: 'Riven — One workspace. Every tool. Immediate insight to action.',
      },
    ],
  },

  twitter: {
    card: 'summary_large_image',
    title: SITE_TITLE,
    description: SITE_DESCRIPTION,
    images: [ogImage],
  },

  robots: {
    index: true,
    follow: true,
    googleBot: {
      index: true,
      follow: true,
      'max-image-preview': 'large',
      'max-snippet': -1,
    },
  },

  icons: {
    icon: '/favicon.ico',
  },
};

export const viewport: Viewport = {
  width: 'device-width',
  initialScale: 1,
  maximumScale: 5,
  userScalable: true,
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`paper-lite ${geistSans.variable} ${geistMono.variable} ${instrumentSerif.variable} ${spaceMono.variable} relative min-h-screen antialiased`}
        style={
          {
            '--paper-texture': `url(${getCdnUrl('images/black-paper.webp')})`,
          } as React.CSSProperties
        }
      >
        <BGPattern
          variant={'diagonal-stripes'}
          size={12}
          fill="color-mix(in srgb, var(--primary) 15%, transparent)"
          className="bg-primary/5"
        />
        <ThemeProvider
          attribute="class"
          defaultTheme="light"
          themes={['light', 'dark', 'amber']}
          disableTransitionOnChange
        >
          <QueryProvider>
            <AuthProvider>
              <Navbar />
              <PageStage>
                {children}
                <Footer />
              </PageStage>
            </AuthProvider>
          </QueryProvider>
          <Toaster richColors position="bottom-right" />
        </ThemeProvider>
      </body>
    </html>
  );
}
