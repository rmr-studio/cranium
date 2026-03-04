'use client';

import { Button } from '@/components/ui/button';
import { CtaButton } from '@/components/ui/cta-button';
import { MobileNavbar } from '@/components/ui/mobile-nav-menu';
import { NAV_LINKS } from '@/lib/navigation';
import { scrollToSection } from '@/lib/scroll';
import { useAuth } from '@/providers/auth-provider';
import { Logo } from '@riven/ui/logo';
import { ThemeToggle } from '@riven/ui/theme-toggle';
import { Github, Menu } from 'lucide-react';
import Link from 'next/link';
import { useEffect, useRef, useState } from 'react';

const CLIENT_URL = process.env.NEXT_PUBLIC_CLIENT_URL || '/dashboard';

export function Navbar() {
  const { user, loading } = useAuth();
  const [isInverted, setIsInverted] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const rafRef = useRef<number>(0);

  useEffect(() => {
    const check = () => {
      const sections = document.querySelectorAll<HTMLElement>('[data-navbar-inverse]');

      const navBottom = 72; // navbar height + top padding
      let inverted = false;
      for (const section of sections) {
        const rect = section.getBoundingClientRect();
        if (rect.top < navBottom && rect.bottom > 0) {
          inverted = true;
          break;
        }
      }
      setIsInverted(inverted);
    };

    const onScroll = () => {
      cancelAnimationFrame(rafRef.current);
      rafRef.current = requestAnimationFrame(check);
    };

    check();
    window.addEventListener('scroll', onScroll, { passive: true });
    return () => {
      window.removeEventListener('scroll', onScroll);
      cancelAnimationFrame(rafRef.current);
    };
  }, []);

  return (
    <header className="fixed top-0 right-0 left-0 z-50 flex items-center px-4 pt-4">
      <nav
        data-navbar=""
        {...(isInverted ? { 'data-inverted': '' } : {})}
        className={`content-nav mx-auto flex h-12 w-auto grow items-center justify-between rounded-md border border-border/50 shadow-sm shadow-primary/35 backdrop-blur-md transition-colors md:h-14 md:rounded-full ${isInverted ? 'bg-background/90' : 'bg-background/60'}`}
      >
        {/* Left: Logo + Nav Links */}
        <div className="flex items-center">
          <Link href="/" className="flex shrink-0 gap-1 px-3 md:px-4">
            <Logo size={32} />
            <div className="mt-1 font-serif text-2xl font-bold text-logo-primary">Riven</div>
          </Link>

          {/* Nav Links - desktop only */}
          <div className="mt-1 hidden items-center gap-1 md:flex">
            {NAV_LINKS.map((link) => (
              <Link
                key={link.label}
                href={link.href}
                onClick={(e) => {
                  const hash = link.href.split('#')[1];
                  if (hash) {
                    e.preventDefault();
                    scrollToSection(hash, link.href);
                  }
                }}
                className="px-4 py-2 text-sm font-medium text-muted-foreground transition-colors hover:text-foreground"
              >
                {link.label}
              </Link>
            ))}
          </div>
        </div>

        {/* Right: ThemeToggle + CTA + Mobile Menu */}
        <div className="flex items-center gap-1.5 px-2 md:gap-2 md:px-4">
          <Link
            href="https://github.com/rmr-studio/riven"
            target="_blank"
            rel="noopener noreferrer"
            className="hidden text-muted-foreground transition-colors hover:text-foreground md:flex"
          >
            <Github className="size-5" />
          </Link>
          <div className="hidden md:block">
            <ThemeToggle />
          </div>
          {!loading && user ? (
            <Link href={CLIENT_URL}>
              <CtaButton size="sm">
                <span>Go to Dashboard</span>
              </CtaButton>
            </Link>
          ) : (
            <Link
              href="/#waitlist"
              onClick={(e) => {
                e.preventDefault();
                scrollToSection('waitlist');
              }}
            >
              <CtaButton size="sm">
                <span className="hidden sm:block">Join the waitlist</span>
                <span className="sm:hidden">Get Started</span>
              </CtaButton>
            </Link>
          )}

          <Button
            variant={'ghost'}
            size={'icon'}
            onClick={() => setMobileMenuOpen(true)}
            className="size-8 shrink-0 cursor-pointer md:hidden"
            aria-label="Open menu"
          >
            <Menu className="size-6 text-primary" />
          </Button>
        </div>
      </nav>

      <MobileNavbar links={NAV_LINKS} open={mobileMenuOpen} setOpen={setMobileMenuOpen} />
    </header>
  );
}
