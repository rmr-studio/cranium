'use client';

import { LinkProps, NavbarProps } from '@/lib/interface';
import { Logo } from '@riven/ui/logo';
import { X } from 'lucide-react';
import Link from 'next/link';
import React, { Dispatch, FC, useEffect, useState } from 'react';
import { Button } from './button';

interface MobileNavbarExtendedProps extends NavbarProps {
  open?: boolean;
  setOpen?: Dispatch<React.SetStateAction<boolean>>;
  showTrigger?: boolean;
}

export const MobileNavbar: FC<MobileNavbarExtendedProps> = ({
  links,
  className,
  open: externalOpen,
  setOpen: externalSetOpen,
}) => {
  const [internalOpen, setInternalOpen] = useState<boolean>(false);
  const isOpen = externalOpen !== undefined ? externalOpen : internalOpen;
  const setIsOpen = externalSetOpen !== undefined ? externalSetOpen : setInternalOpen;

  // Track mounted state for CSS transition (mount → animate in, unmount after animate out)
  const [mounted, setMounted] = useState(false);
  const [visible, setVisible] = useState(false);

  useEffect(() => {
    if (isOpen) {
      setMounted(true);
      // Request animation frame to ensure the mount renders before adding visible class
      requestAnimationFrame(() => {
        requestAnimationFrame(() => setVisible(true));
      });
    } else {
      setVisible(false);
      const timer = setTimeout(() => setMounted(false), 400);
      return () => clearTimeout(timer);
    }
  }, [isOpen]);

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
        className="absolute right-0 bottom-0 left-2 z-[101] rounded-tl-md border-t border-border/20 bg-background transition-transform duration-[450ms]"
        style={{
          transform: visible ? 'translateY(0%)' : 'translateY(100%)',
          transitionTimingFunction: 'cubic-bezier(0.22, 1, 0.36, 1)',
        }}
      >
        {/* Header */}
        <div className="flex items-center justify-between px-6 pt-5 pb-3">
          <div className="flex items-center gap-3">
            <Link href="/" onClick={() => setIsOpen(false)} className="flex items-center gap-1.5">
              <Logo size={22} className="fill-logo-primary" />
              <span className="mt-0.5 text-lg font-bold text-logo-primary">Riven</span>
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

        {/* Links + CTA */}
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
                style={{
                  opacity: visible ? 1 : 0,
                  transform: visible ? 'translateY(0)' : 'translateY(8px)',
                  transition: `opacity 0.3s cubic-bezier(0.22, 1, 0.36, 1) ${0.07 * index}s, transform 0.3s cubic-bezier(0.22, 1, 0.36, 1) ${0.07 * index}s`,
                }}
              />
            ))}
            <MobileNavLink
              key={`mobile-link-cta`}
              toggle={setIsOpen}
              label={'Join the waitlist'}
              href="/#waitlist"
              external={false}
              shouldCloseOnClick={true}
              style={{
                opacity: visible ? 1 : 0,
                transform: visible ? 'translateY(0)' : 'translateY(8px)',
                transition: `opacity 0.3s cubic-bezier(0.22, 1, 0.36, 1) ${0.07 * links.length}s, transform 0.3s cubic-bezier(0.22, 1, 0.36, 1) ${0.07 * links.length}s`,
              }}
            />
          </div>
        </nav>
      </div>
    </div>
  );
};

/* ─── link item ─── */

interface NavbarItemProps extends LinkProps {
  toggle: Dispatch<React.SetStateAction<boolean>>;
  style?: React.CSSProperties;
}

const MobileNavLink: FC<NavbarItemProps> = ({
  label,
  href,
  toggle,
  shouldCloseOnClick = true,
  external = false,
  style,
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
    <div style={style}>
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
    </div>
  );
};
