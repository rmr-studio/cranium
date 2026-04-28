'use client';

import { NAV_LINKS } from '@/lib/navigation';
import { scrollToSection } from '@/lib/scroll';
import { Logo } from '@riven/ui/logo';
import Link from 'next/link';

const footerLinks = {
  product: NAV_LINKS,
  legal: [{ label: 'Privacy Policy', href: '/privacy' }],
};

const socialLinks = [
  {
    label: 'X (Formerly Twitter)',
    href: 'https://x.com/withriven',
  },
  {
    label: 'LinkedIn',
    href: 'https://linkedin.com/company/getriven',
  },
];

export function Footer() {
  const currentYear = new Date().getFullYear();

  return (
    <footer className="border-t bg-background px-8 pt-14 pb-10 md:px-8 md:pt-20 md:pb-14 lg:px-12">
      <div className="mx-auto max-w-7xl">
        {/* Top: Logo/tagline + link columns */}
        <div className="flex flex-col gap-10 md:flex-row md:justify-between">
          <div className="mx-auto flex flex-col sm:mx-0">
            <Link href="/" className="flex items-center gap-2.5">
              <Logo size={74} className="mt-4 scale-80 sm:scale-100" />
              <span className="text-6xl font-normal tracking-tight text-primary sm:ml-4 md:text-8xl">
                riven
              </span>
            </Link>
            <div className="mx-auto tracking-tighter text-muted-foreground sm:mx-0 sm:text-xl">
              Move fast. Act fast. Grow Fast.
            </div>
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
