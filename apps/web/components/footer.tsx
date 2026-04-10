'use client';

import { NAV_LINKS } from '@/lib/navigation';
import { scrollToSection } from '@/lib/scroll';
import { useMounted } from '@riven/hooks';
import { Button } from '@riven/ui/button';
import { LogoBackground } from '@riven/ui/logo';
import { Moon, Sun } from 'lucide-react';
import { useTheme } from 'next-themes';
import Link from 'next/link';

const footerLinks = {
  product: NAV_LINKS,
  legal: [{ label: 'Privacy Policy', href: '/privacy' }],
};

const socialLinks = [
  // {
  //   label: 'X (Formerly Twitter)',
  //   href: 'https://x.com/riven_app',
  // },
  {
    label: 'LinkedIn',
    href: 'https://linkedin.com/company/getriven',
  },
];

export function Footer() {
  const currentYear = new Date().getFullYear();
  const { theme, setTheme } = useTheme();
  const mounted = useMounted();

  // Until next-themes resolves on the client, render as if dark (matches
  // ThemeProvider's defaultTheme="dark"). Without this gate the toggle's
  // icon and label flip after hydration, causing a structural mismatch
  // and a footer subtree re-render.
  const resolvedTheme = mounted ? theme : 'dark';

  const toggleTheme = () => {
    setTheme(resolvedTheme === 'dark' ? 'light' : 'dark');
  };

  return (
    <footer className="paper-lite border-t bg-background px-8 pt-14 pb-10 md:px-8 md:pt-20 md:pb-14 lg:px-12">
      <div className="mx-auto max-w-7xl">
        {/* Top: Logo/tagline + link columns */}
        <div className="flex flex-col gap-10 md:flex-row md:justify-between">
          <div className="mx-auto flex flex-col sm:mx-0">
            <Link href="/" className="flex items-center gap-2.5">
              <LogoBackground
                size={102}
                className="scale-80 fill-primary sm:scale-100"
                logoClassname="fill-background"
              />
              <span className="mt-2 font-serif text-6xl font-normal tracking-tight text-primary sm:ml-4 md:text-8xl">
                Riven
              </span>
            </Link>
            <div className="mx-auto tracking-tight text-muted-foreground sm:mx-0 sm:text-xl">
              Move fast. Act fast. Grow Fast.
            </div>
            <Button
              variant="outline"
              className="mt-6 hidden w-max border-primary sm:flex"
              onClick={toggleTheme}
            >
              {resolvedTheme === 'dark' ? <Sun /> : <Moon />}
              {resolvedTheme === 'dark' ? 'Light' : 'Dark'} Mode
            </Button>
          </div>

          <div className="flex gap-16 md:gap-20">
            <div className="flex flex-col gap-3">
              <h3 className="font-display text-xs font-bold tracking-[0.05em] text-foreground uppercase">
                Socials
              </h3>
              <ul className="flex flex-col gap-2.5">
                {socialLinks.map((link) => (
                  <li key={link.label}>
                    <Link
                      target="_blank"
                      href={link.href}
                      className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
            <div className="flex flex-col gap-3">
              <h3 className="font-display text-xs font-bold tracking-[0.05em] text-foreground uppercase">
                Product
              </h3>
              <ul className="flex flex-col gap-2.5">
                {footerLinks.product.map((link) => (
                  <li key={link.label}>
                    <Link
                      href={link.href}
                      onClick={(e) => {
                        const hash = link.href.split('#')[1];
                        if (hash) {
                          e.preventDefault();
                          scrollToSection(hash, link.href);
                        }
                      }}
                      className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>

            <div className="flex flex-col gap-3">
              <h3 className="font-display text-xs font-bold tracking-[0.05em] text-foreground uppercase">
                Legal
              </h3>
              <ul className="flex flex-col gap-2.5">
                {footerLinks.legal.map((link) => (
                  <li key={link.label}>
                    <Link
                      href={link.href}
                      className="text-sm text-muted-foreground transition-colors hover:text-foreground"
                    >
                      {link.label}
                    </Link>
                  </li>
                ))}
              </ul>
            </div>
          </div>
        </div>

        {/* Bottom: Copyright + email */}
        <div className="mt-10 flex flex-col items-center gap-2 text-center">
          <p className="text-sm text-muted-foreground">
            &copy; {currentYear} Riven. All rights reserved.
          </p>
        </div>
      </div>
    </footer>
  );
}
