import { Footer } from '@/components/footer';
import { Navbar } from '@/components/navbar';
import { SmoothScrollProvider } from '@/components/smooth-scroll-provider';
import { AuthProvider } from '@/providers/auth-provider';
import { QueryProvider } from '@/providers/query-provider';

import { GoogleTagManager } from '@next/third-parties/google';
import type { Metadata, Viewport } from 'next';
import { DotGothic16, Geist, Geist_Mono, Space_Mono } from 'next/font/google';
import { Toaster } from 'sonner';
import './globals.css';

const dotGothic = DotGothic16({
  variable: '--font-dot-gothic',
  subsets: ['latin'],
  weight: ['400'],
});

const geistSans = Geist({
  variable: '--font-geist',
  subsets: ['latin'],
  weight: ['400', '500', '600', '700'],
});

const geistMono = Geist_Mono({
  variable: '--font-geist-mono',
  subsets: ['latin'],
});

const spaceMono = Space_Mono({
  variable: '--font-space-mono',
  subsets: ['latin'],
  weight: ['400', '700'],
});

const SITE_TITLE = 'Riven | Proactive Company Brain for Ecommerce';
const SITE_DESCRIPTION =
  'Move fast. Act fast. Grow Fast. Riven builds the brain that brings context in from each tool, note and email you have ever sent. Finding, executing monitoring and learning from the most impactful opportunities, trends, problems and risks, before they even arise.';

const ogImage = process.env.NEXT_PUBLIC_CDN_URL
  ? `${process.env.NEXT_PUBLIC_CDN_URL}/images/og-image.jpg`
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
    'autonomous business intelligence',
    'growth platform',
    'customer data platform',
    'customer insights platform',
    'SaaS customer analytics',
    'Ecommerce customer insights',
    'cross-domain intelligence',
    'churn analysis',
    'churn retrospective',
    'unified customer data',
    'customer retention platform',
    'AI business intelligence',
    'Stripe CRM integration',
    'support data analytics',
    'plain English data queries',
    'operational intelligence',
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
        alt: 'Riven | Autonomous Intelligence & Growth Platform',
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
    icon: [
      { url: '/favicon.ico', sizes: 'any' },
      { url: '/favicon.svg', type: 'image/svg+xml' },
      { url: '/web-app-manifest-192x192.png', sizes: '192x192', type: 'image/png' },
      { url: '/web-app-manifest-512x512.png', sizes: '512x512', type: 'image/png' },
    ],
    apple: '/apple-icon.png',
  },
  manifest: '/manifest.json',
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
      {process.env.NEXT_PUBLIC_GTM_ID && (
        <GoogleTagManager gtmId={process.env.NEXT_PUBLIC_GTM_ID} />
      )}
      <body
        className={` ${geistSans.variable} ${dotGothic.variable} ${geistMono.variable} ${spaceMono.variable} relative min-h-screen overflow-x-clip antialiased`}
      >
        <QueryProvider>
          <AuthProvider>
            <SmoothScrollProvider>
              <section>
                <Navbar />

                {children}
              </section>

              <Footer />
            </SmoothScrollProvider>
          </AuthProvider>
        </QueryProvider>
        <Toaster richColors position="bottom-right" />
      </body>
    </html>
  );
}
