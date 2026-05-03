'use client';

import { NAV_LINKS } from '@/lib/navigation';
import { scrollToSection } from '@/lib/scroll';
import { CornerDownRight } from 'lucide-react';
import Link from 'next/link';

const footerLinks = {
  product: NAV_LINKS,
  legal: [{ label: 'Privacy Policy', href: '/privacy' }],
};

const socialLinks = [
  {
    label: 'X',
    href: 'https://x.com/withriven',
  },
  {
    label: 'LinkedIn',
    href: 'https://linkedin.com/company/getriven',
  },
];

export function Footer() {
  return (
    <footer className="z-[0] border-t border-t-content/35 bg-background">
      <div className="flex flex-col space-y-4 px-6 py-8 text-xs sm:flex-row sm:px-20 sm:text-sm">
        <div className="w-fit text-start font-display text-content">getriven.io</div>
        <div className="w-fit font-display text-content">Proactive Ecommerce Company Brain</div>
        <div className="w-fit grow text-end font-display text-content">
          Think Fast. Act Fast. Grow Fast
        </div>
      </div>
      <div className="flex flex-wrap items-start gap-y-8 border-b border-content/30 px-6 py-8 sm:flex-nowrap sm:px-20">
        <div className="w-1/2 font-display text-content sm:w-32">
          <h3 className="font-display text-xs font-bold tracking-[0.05em] text-foreground uppercase">
            Menu
          </h3>
          <ul className="flex flex-col">
            {footerLinks.product.map((link) => (
              <li key={link.label} className="mx-0.5 flex items-center gap-2">
                <CornerDownRight className="size-3 text-content" />
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
        <div className="w-1/2 font-display text-content sm:w-32">
          <h3 className="font-display text-xs font-bold tracking-[0.05em] text-foreground uppercase">
            Follow
          </h3>
          <ul className="flex flex-col">
            {socialLinks.map((link) => (
              <li key={link.label} className="mx-0.5 flex items-center gap-2">
                <CornerDownRight className="size-3 text-content" />
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

        <div className="flex w-full justify-start font-display text-content sm:w-auto sm:grow sm:justify-end">
          <div className="font-display text-content sm:w-68">
            <h3 className="font-display text-xs font-bold tracking-[0.05em] text-foreground uppercase">
              Privacy & Terms
            </h3>
            <ul className="flex flex-col">
              {footerLinks.legal.map((link) => (
                <li key={link.label} className="flex items-center gap-2">
                  <CornerDownRight className="size-3 text-content" />
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

      <div className="overflow-hidden font-bit text-[32vw] leading-none font-semibold whitespace-nowrap">
        riven
      </div>
    </footer>
  );
}
