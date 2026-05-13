'use client';

import { ResourcesMenu } from '@/components/navbar/resources-menu';
import { Button } from '@/components/ui/button';
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
import { Logo } from '@cranium/ui/logo';
import { Mail, Menu } from 'lucide-react';
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
    <header className="fixed top-0 right-0 left-0 z-90">
      <nav
        data-navbar=""
        data-inverted={isInverted || undefined}
        className="mx-auto hidden h-2 w-full bg-background/40 backdrop-blur-2xl sm:block md:h-4"
      >
        <div className="clamp w-inherit mx-auto flex h-full grow border-x border-x-content/15" />
      </nav>
      {/* Full-width navbar bar with top + bottom borders spanning the entire viewport */}
      <nav
        data-navbar=""
        data-inverted={isInverted || undefined}
        className="flex h-16 w-full items-center justify-between border-y bg-background/40 shadow-lg backdrop-blur-2xl md:h-14"
      >
        {/* Inner content clamped to panel width */}
        <div className="clamp mx-auto flex h-full w-full items-center justify-between">
          {/* Left: Logo + Nav Links */}
          <div className="flex items-center">
            <Link href="/" className="flex shrink-0 gap-1 px-3 md:px-4">
              <Logo
                size={18}
                className="mt-1.5 rounded-md"
                primaryClassName="fill-logo-primary"
                secondaryClassName="fill-logo-secondary"
                tertiaryClassName="fill-logo-tertiary"
              />
              <div className="font-display text-2xl tracking-tighter text-primary">cranium</div>
            </Link>

            {/* Nav Links - desktop only */}
            <div className="mt-1 hidden items-center md:flex">
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
                <Button
                  className="border-primary/70 bg-transparent px-1.5 py-0.5 font-display text-xs text-content hover:bg-accent hover:text-accent-foreground"
                  variant={'outline'}
                  size={'sm'}
                >
                  Go to Dashboard
                </Button>
              </Link>
            ) : (
              <Link
                href="/#waitlist"
                onClick={(e) => {
                  e.preventDefault();
                  scrollToSection('waitlist');
                }}
                className="hidden min-[360px]:block"
              >
                <Button
                  className="border-primary/70 bg-transparent px-1.5 py-0.5 font-display text-xs text-content hover:bg-accent hover:text-accent-foreground"
                  variant={'outline'}
                  size={'sm'}
                >
                  <Mail size={14} />
                  Join the Waitlist
                </Button>
              </Link>
            )}

            <Button
              variant={'outline'}
              size={'icon'}
              onClick={() => setMobileMenuOpen(true)}
              className="size-8 shrink-0 cursor-pointer rounded-md border-primary/70 bg-transparent text-content hover:bg-transparent focus:bg-transparent md:hidden"
              aria-label="Open menu"
            >
              <Menu className="size-4 text-content" />
            </Button>
          </div>
        </div>
      </nav>

      <MobileNavbar links={NAV_LINKS_FLAT} open={mobileMenuOpen} setOpen={setMobileMenuOpen} />
    </header>
  );
}
