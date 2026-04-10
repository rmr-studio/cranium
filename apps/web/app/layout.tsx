import { Footer } from '@/components/footer';
import { Navbar } from '@/components/navbar';
import { getCdnUrl } from '@/lib/cdn-image-loader';
import { AuthProvider } from '@/providers/auth-provider';
import { QueryProvider } from '@/providers/query-provider';

import { ThemeProvider } from '@/providers/theme-provider';
import { GoogleTagManager } from '@next/third-parties/google';
import type { Metadata, Viewport } from 'next';
import { Geist, Geist_Mono, Space_Mono } from 'next/font/google';
import localFont from 'next/font/local';
import { Toaster } from 'sonner';
import './globals.css';

const redaction = localFont({
  src: [
    {
      path: '../public/fonts/redaction/webfonts/Redaction-Regular.woff2',
      weight: '400',
      style: 'normal',
    },
    {
      path: '../public/fonts/redaction/webfonts/Redaction-Bold.woff2',
      weight: '700',
      style: 'normal',
    },
    {
      path: '../public/fonts/redaction/webfonts/Redaction-Italic.woff2',
      weight: '400',
      style: 'italic',
    },
  ],
  variable: '--font-redaction',
  display: 'swap',
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

const SITE_TITLE = 'Riven | Autonomous Intelligence & Growth Platform';
const SITE_DESCRIPTION =
  'Move fast. Act fast. Grow Fast. One workspace for your customer lifecycle stack. From marketing to CRMs to payments, analytics and support. Cross-domain intelligence surfaces churn risks, hidden patterns, and growth opportunities no single tool can see. Query across every platform in plain English, tag and track accounts, and act on insights without leaving the tab.';

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
    'customer insights platform',
    'SaaS customer analytics',
    'Ecommerce customer insights',
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
      {process.env.NEXT_PUBLIC_GTM_ID && (
        <GoogleTagManager gtmId={process.env.NEXT_PUBLIC_GTM_ID} />
      )}
      <body
        className={`paper-lite ${geistSans.variable} ${redaction.variable} ${geistMono.variable} ${spaceMono.variable} relative min-h-screen antialiased`}
        style={
          {
            '--paper-texture': `url(${getCdnUrl('images/black-paper.webp')})`,
          } as React.CSSProperties
        }
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="dark"
          themes={['light', 'dark']}
          disableTransitionOnChange
        >
          <QueryProvider>
            <AuthProvider>
              <Navbar />
              <section className="relative mx-auto w-full lg:max-w-[min(100dvw,var(--breakpoint-3xl))]">
                {children}
              </section>
              <Footer />
            </AuthProvider>
          </QueryProvider>
          <Toaster richColors position="bottom-right" />
        </ThemeProvider>
      </body>
    </html>
  );
}
