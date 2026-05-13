'use client';

import { DitherTransition } from '@/components/ui/dither-transition';
import { LinkProps, NavbarProps } from '@/lib/interface';
import { Logo } from '@cranium/ui/logo';
import { X } from 'lucide-react';
import Link from 'next/link';
import React, { Dispatch, FC, useEffect, useState } from 'react';
import { Button } from './button';

const ENTER_MS = 820;
const EXIT_MS = 560;

interface MobileNavbarExtendedProps extends NavbarProps {
  open?: boolean;
  setOpen?: Dispatch<React.SetStateAction<boolean>>;
  showTrigger?: boolean;
}

export const MobileNavbar: FC<MobileNavbarExtendedProps> = ({
  links,
  open: externalOpen,
  setOpen: externalSetOpen,
}) => {
  const [internalOpen, setInternalOpen] = useState<boolean>(false);
  const isOpen = externalOpen !== undefined ? externalOpen : internalOpen;
  const setIsOpen = externalSetOpen !== undefined ? externalSetOpen : setInternalOpen;

  const [mounted, setMounted] = useState(isOpen);
  const [visible, setVisible] = useState(false);
  const [prevIsOpen, setPrevIsOpen] = useState(isOpen);

  // React docs "storing info from previous renders" pattern: drive mount + visible
  // off the isOpen prop edge during render so the dither rAF isn't racing a setState-in-effect.
  if (isOpen !== prevIsOpen) {
    setPrevIsOpen(isOpen);
    if (isOpen) setMounted(true);
    else setVisible(false);
  }

  useEffect(() => {
    if (!mounted || !isOpen) return;
    let inner = 0;
    const outer = requestAnimationFrame(() => {
      inner = requestAnimationFrame(() => setVisible(true));
    });
    return () => {
      cancelAnimationFrame(outer);
      cancelAnimationFrame(inner);
    };
  }, [mounted, isOpen]);

  if (!mounted) return null;

  return (
    <div
      onClick={() => setIsOpen(false)}
      data-mobile-nav
      className="fixed inset-0 z-[100] transition-opacity duration-300 ease-out"
      style={{
        backgroundColor: visible ? 'rgba(10, 10, 10, 0.5)' : 'transparent',
        backdropFilter: visible ? 'blur(4px)' : 'none',
      }}
    >
      <div
        onClick={(e) => e.stopPropagation()}
        className="absolute right-0 bottom-0 left-2 z-[101] overflow-hidden rounded-tl-md"
      >
        <DitherTransition
          active={visible}
          duration={visible ? ENTER_MS : EXIT_MS}
          fillColor="var(--background)"
          direction="bottom-up"
          pattern="noise"
          erosionWeight={0.42}
          onExited={() => setMounted(false)}
        />

        <div
          className="relative z-10 border-t border-border/20"
          style={{
            opacity: visible ? 1 : 0,
            transition: visible
              ? `opacity 220ms ease-out ${Math.round(ENTER_MS * 0.55)}ms`
              : 'opacity 140ms ease-out',
          }}
        >
          <div className="flex items-center justify-between px-6 pt-5 pb-3">
            <div className="flex items-center gap-3">
              <Link href="/" onClick={() => setIsOpen(false)} className="flex items-center gap-1.5">
                <Logo size={22} className="fill-logo-primary" />
                <span className="mt-0.5 text-lg font-bold text-logo-primary">Cranium</span>
              </Link>
            </div>
            <Button
              variant="ghost"
              size="icon"
              onClick={() => setIsOpen(false)}
              className="size-9 text-muted-foreground hover:text-foreground"
            >
              <X className="size-5" />
            </Button>
          </div>

          <nav className="px-6 pt-4 pb-10">
            <div className="ml-2">
              {links.map((link, index) => (
                <MobileNavLink
                  key={`mobile-link-${index}`}
                  toggle={setIsOpen}
                  label={link.label}
                  href={link.href}
                  external={link.external}
                  shouldCloseOnClick={link.shouldCloseOnClick}
                />
              ))}
              <MobileNavLink
                key="mobile-link-cta"
                toggle={setIsOpen}
                label="Join the waitlist"
                href="/#waitlist"
                external={false}
                shouldCloseOnClick={true}
              />
            </div>
          </nav>
        </div>
      </div>
    </div>
  );
};

/* ─── link item ─── */

interface NavbarItemProps extends LinkProps {
  toggle: Dispatch<React.SetStateAction<boolean>>;
}

const MobileNavLink: FC<NavbarItemProps> = ({
  label,
  href,
  toggle,
  shouldCloseOnClick = true,
  external = false,
}) => {
  const handleClick = (e: React.MouseEvent<HTMLAnchorElement>) => {
    const hash = href.includes('#') ? href.split('#')[1] : null;
    if (hash) {
      e.preventDefault();
      const element = document.getElementById(hash);
      if (element) {
        element.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
      window.history.replaceState(null, '', `#${hash}`);
    }
    if (shouldCloseOnClick) {
      toggle(false);
    }
  };

  return (
    <Link
      href={href}
      onClick={handleClick}
      target={external ? '_blank' : '_self'}
      className="group flex items-center border border-border border-t-transparent border-r-transparent py-3.5 pl-4 transition-colors hover:border-foreground/60"
    >
      <span className="text-lg font-semibold tracking-wide text-muted-foreground uppercase transition-colors group-hover:text-foreground">
        {label}
      </span>
    </Link>
  );
};
