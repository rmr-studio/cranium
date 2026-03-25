import { Footer } from '@/components/footer';
import { Navbar } from '@/components/navbar';
import { PageStage } from '@/components/page-stage';
import { BGPattern } from '@/components/ui/background/grids';
import { getCdnUrl } from '@/lib/cdn-image-loader';
import { AuthProvider } from '@/providers/auth-provider';
import { LazyQueryProvider as QueryProvider } from '@/providers/lazy-query-provider';
import { MotionProvider } from '@/providers/motion-provider';
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

export const metadata: Metadata = {
  metadataBase: new URL(process.env.NEXT_PUBLIC_SITE_URL || 'https://getriven.io'),

  title: {
    default: 'Riven | Cross-Domain Intelligence for Scaling Businesses',
    template: '%s | Riven',
  },

  description:
    "AI powered data platform enabling cross-domain intelligence for scaling consumer-facing businesses. Connect your CRM, payments, support, and product data with an AI layer that surfaces what siloed tools can't.",

  keywords: [
    'cross-domain intelligence',
    'business intelligence platform',
    'unified data ecosystem',
    'AI knowledge layer',
    'business operations platform',
    'workflow automation',
    'data unification',
    'operational intelligence',
    'scaling startups',
    'integration platform',
  ],

  openGraph: {
    title: 'Riven | Cross-Domain Intelligence for Scaling Businesses',
    description:
      "AI powered data platform enabling cross-domain intelligence for scaling consumer-facing businesses. Connect your CRM, payments, support, and product data with an AI layer that surfaces what siloed tools can't.",
    url: 'https://getriven.io',
    siteName: 'Riven',
    locale: 'en_US',
    type: 'website',
    images: [
      {
        url: process.env.NEXT_PUBLIC_CDN_URL
          ? `${process.env.NEXT_PUBLIC_CDN_URL}/images/og-image.png`
          : '/og-image.png',
        width: 1200,
        height: 630,
        alt: 'Riven — Cross-Domain Intelligence. One Business',
      },
    ],
  },

  twitter: {
    card: 'summary_large_image',
    title: 'Riven | Cross-Domain Intelligence for Scaling Businesses',
    description:
      "AI powered data platform enabling cross-domain intelligence for scaling consumer-facing businesses. Connect your CRM, payments, support, and product data with an AI layer that surfaces what siloed tools can't.",
    images: [
      process.env.NEXT_PUBLIC_CDN_URL
        ? `${process.env.NEXT_PUBLIC_CDN_URL}/images/og-image.png`
        : '/og-image.png',
    ],
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
        className={`${geistSans.variable} ${geistMono.variable} ${instrumentSerif.variable} ${spaceMono.variable} relative min-h-screen antialiased`}
        style={
          {
            '--paper-texture': `url(${getCdnUrl('images/black-paper.webp')})`,
          } as React.CSSProperties
        }
      >
        <BGPattern
          variant={'diagonal-stripes'}
          size={24}
          fill="color-mix(in srgb, var(--primary) 15%, transparent)"
          className="bg-primary/5"
        />
        <ThemeProvider
          attribute="class"
          defaultTheme="light"
          themes={['light', 'dark', 'amber']}
          disableTransitionOnChange
        >
          <MotionProvider>
            <QueryProvider>
              <AuthProvider>
                <Navbar />
                <PageStage>
                  {children}
                  <Footer />
                </PageStage>
              </AuthProvider>
            </QueryProvider>
          </MotionProvider>
          <Toaster richColors position="bottom-right" />
        </ThemeProvider>
      </body>
    </html>
  );
}
