import { AuthProvider } from '@/components/provider/auth-context';
import { ThemeProvider } from '@/components/provider/ThemeContext';
import { WebSocketProvider } from '@/components/provider/websocket-provider';
import QueryClientWrapper from '@/components/util/query.wrapper';
import StoreProviderWrapper from '@/components/util/store.wrapper';
import type { Metadata } from 'next';
import { Geist, Geist_Mono, Instrument_Serif, Space_Mono } from 'next/font/google';
import localFont from 'next/font/local';
import { Toaster } from 'sonner';

import '@/components/feature-modules/blocks/styles/gridstack-custom.css';
import { getCdnUrl } from '@/lib/util/utils';
import '@xyflow/react/dist/style.css';
import 'gridstack/dist/gridstack.css';
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

const SITE_TITLE = 'Riven | Dashboard';
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

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html lang="en" suppressHydrationWarning>
      <body
        className={`paper-lite ${geistSans.variable} ${redaction.variable} ${geistMono.variable} ${instrumentSerif.variable} ${spaceMono.variable} antialiased`}
        style={
          {
            '--paper-texture': `url(${getCdnUrl('images/black-paper.webp')})`,
          } as React.CSSProperties
        }
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          themes={['light', 'dark']}
          disableTransitionOnChange
        >
          <AuthProvider>
            <QueryClientWrapper>
              <WebSocketProvider>
                <StoreProviderWrapper>
                  <main className="relative w-full">{children}</main>
                </StoreProviderWrapper>
              </WebSocketProvider>
            </QueryClientWrapper>
          </AuthProvider>
        </ThemeProvider>
        <Toaster richColors />
      </body>
    </html>
  );
}
