import { AuthProvider } from '@/components/provider/auth-context';
import { ThemeProvider } from '@/components/provider/ThemeContext';
import { WebSocketProvider } from '@/components/provider/websocket-provider';
import QueryClientWrapper from '@/components/util/query.wrapper';
import StoreProviderWrapper from '@/components/util/store.wrapper';
import type { Metadata } from 'next';
import { Geist, Geist_Mono, Instrument_Serif, Space_Mono } from 'next/font/google';
import { Toaster } from 'sonner';

import '@/components/feature-modules/blocks/styles/gridstack-custom.css';
import '@xyflow/react/dist/style.css';
import 'gridstack/dist/gridstack.css';
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
  title: 'Riven | Dashboard',
  description:
    'Riven is the next step in managing your invoices, clients and reports. Designed for all types of businesses, big, small or solo.',
  openGraph: {
    locale: 'en_AU',
    type: 'website',
    url: 'https://app.getriven.io',
    title: 'Riven | Dashboard',
    description:
      'Riven is the next step in managing your invoices, clients and reports.',
    siteName: 'Riven',
  },
  robots: {
    index: false,
    follow: false,
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
        className={`${geistSans.variable} ${geistMono.variable} ${instrumentSerif.variable} ${spaceMono.variable} antialiased`}
      >
        <ThemeProvider
          attribute="class"
          defaultTheme="system"
          enableSystem
          themes={['light', 'dark', 'amber']}
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
