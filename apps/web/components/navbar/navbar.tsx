'use client';

import { ResourcesMenu } from '@/components/navbar/resources-menu';
import { Button } from '@/components/ui/button';
import { CtaButton } from '@/components/ui/cta-button';
import { MobileNavbar } from '@/components/ui/mobile-nav-menu';
import {
  NavigationMenu,
  NavigationMenuItem,
  NavigationMenuLink,
  NavigationMenuList,
  navigationMenuTriggerStyle,
} from '@/components/ui/navigation-menu';
import { NAV_LINKS_FLAT } from '@/lib/navigation';
import { scrollToHashOnLoad, scrollToSection } from '@/lib/scroll';
import { cn } from '@/lib/utils';
import { useAuth } from '@/providers/auth-provider';
import { Logo } from '@riven/ui/logo';
import { Menu } from 'lucide-react';
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

  useEffect(() => {
    scrollToHashOnLoad();
  }, []);

  return (
    <header className="fixed top-0 right-0 left-0 z-50">
      <nav
        data-navbar=""
        {...(isInverted ? { 'data-inverted': '' } : {})}
        className="mx-auto h-2 w-full bg-background md:h-4"
      >
        <div className="clamp w-inherit mx-auto flex h-full grow border-x" />
      </nav>
      {/* Full-width navbar bar with top + bottom borders spanning the entire viewport */}
      <nav
        data-navbar=""
        {...(isInverted ? { 'data-inverted': '' } : {})}
        className={`paper-lite flex h-16 w-full items-center justify-between border-y bg-background shadow-lg md:h-14`}
      >
        {/* Inner content clamped to panel width */}
        <div className="clamp mx-auto flex h-full w-full items-center justify-between border-x">
          {/* Left: Logo + Nav Links */}
          <div className="flex items-center">
            <Link href="/" className="flex shrink-0 gap-1 px-3 md:px-4">
              <Logo
                size={30}
                className="mt-0.5 mr-1 rounded-md"
                primaryClassName="fill-logo-primary"
                secondaryClassName="fill-logo-secondary"
                tertiaryClassName="fill-logo-tertiary"
              />
              <div className="mt-1 font-serif text-[2rem] tracking-tighter text-primary">Riven</div>
            </Link>

            {/* Nav Links - desktop only */}
            <div className="mt-0.5 hidden items-center md:flex">
              <NavigationMenu>
                <NavigationMenuList>
                  {/* Features */}
                  <NavigationMenuItem asChild>
                    <NavigationMenuLink
                      asChild
                      className={cn(
                        navigationMenuTriggerStyle(),
                        'hover:bg-transparent hover:font-semibold focus:bg-transparent',
                      )}
                    >
                      <Link
                        href="/#features"
                        onClick={(e) => {
                          e.preventDefault();
                          scrollToSection('features', '/#features');
                        }}
                      >
                        <span className="nav-link-stable" data-text="Features">
                          Features
                        </span>
                      </Link>
                    </NavigationMenuLink>
                  </NavigationMenuItem>

                  <NavigationMenuItem asChild>
                    <NavigationMenuLink
                      asChild
                      className={cn(
                        navigationMenuTriggerStyle(),
                        'hover:bg-transparent hover:font-semibold focus:bg-transparent',
                      )}
                    >
                      <Link href="/story">
                        <span className="nav-link-stable" data-text="Story">
                          Story
                        </span>
                      </Link>
                    </NavigationMenuLink>
                  </NavigationMenuItem>
                  {/* Resources */}
                  <ResourcesMenu />
                </NavigationMenuList>
              </NavigationMenu>
            </div>
          </div>

          {/* Right: ThemeToggle + CTA + Mobile Menu */}
          <div className="flex items-center gap-1.5 px-2 md:gap-2 md:px-4">
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
        </div>
      </nav>

      <MobileNavbar links={NAV_LINKS_FLAT} open={mobileMenuOpen} setOpen={setMobileMenuOpen} />
    </header>
  );
}
