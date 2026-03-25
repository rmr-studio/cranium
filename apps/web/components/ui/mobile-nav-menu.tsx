'use client';

import { LinkProps, NavbarProps } from '@/lib/interface';
import { Logo } from '@riven/ui/logo';
import { ThemeToggle } from '@riven/ui/theme-toggle';
import { Github, X } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import Link from 'next/link';
import React, { Dispatch, FC, useState } from 'react';
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

  // Use external state if provided, otherwise use internal state
  const isOpen = externalOpen !== undefined ? externalOpen : internalOpen;
  const setIsOpen = externalSetOpen !== undefined ? externalSetOpen : setInternalOpen;

  return (
    <AnimatePresence>
      {isOpen ? <DrawerSheet links={links} toggle={setIsOpen} className={className} /> : null}
    </AnimatePresence>
  );
};

/* ─── animation variants ─── */

const backdropVars = {
  initial: { opacity: 0 },
  animate: { opacity: 1, transition: { duration: 0.3, ease: [0.22, 1, 0.36, 1] as const } },
  exit: { opacity: 0, transition: { delay: 0.3, duration: 0.25 } },
};

const drawerVars = {
  initial: { y: '100%' },
  animate: {
    y: '0%',
    transition: { duration: 0.45, ease: [0.22, 1, 0.36, 1] as const },
  },
  exit: {
    y: '100%',
    transition: { delay: 0.15, duration: 0.35, ease: [0.55, 0, 1, 0.45] as const },
  },
};

const staggerContainer = {
  initial: { transition: { staggerChildren: 0.04, staggerDirection: -1 } },
  animate: { transition: { staggerChildren: 0.07 } },
  exit: { transition: { staggerChildren: 0.03, staggerDirection: -1 } },
};

const linkBlockVars = {
  initial: {
    opacity: 0,
    y: 8,
  },
  animate: {
    opacity: 1,
    y: 0,
    transition: { duration: 0.3, ease: [0.22, 1, 0.36, 1] as const },
  },
  exit: {
    opacity: 0,
    y: 8,
    transition: { duration: 0.15 },
  },
};

/* ─── drawer ─── */

interface NavbarMenuProps extends NavbarProps {
  toggle: Dispatch<React.SetStateAction<boolean>>;
}

const DrawerSheet: FC<NavbarMenuProps> = ({ links, toggle }) => {
  return (
    <motion.div
      onClick={() => toggle(false)}
      variants={backdropVars}
      initial="initial"
      animate="animate"
      exit="exit"
      data-mobile-nav
      className="fixed inset-0 z-[100] bg-neutral-950/50 backdrop-blur-sm"
    >
      <motion.div
        onClick={(e) => e.stopPropagation()}
        variants={drawerVars}
        initial="initial"
        animate="animate"
        exit="exit"
        className="absolute right-0 bottom-0 left-2 z-[101] rounded-tl-md border-t border-border/20 bg-background"
      >
        {/* Header */}
        <div className="paper-lite flex items-center justify-between px-6 pt-5 pb-3">
          <div className="flex items-center gap-3">
            <Link href="/" onClick={() => toggle(false)} className="flex items-center gap-1.5">
              <Logo size={22} className="fill-logo-primary" />
              <span className="mt-0.5 font-serif text-lg font-bold text-logo-primary">Riven</span>
            </Link>
            <Link
              href="https://github.com/rmr-studio/riven"
              target="_blank"
              rel="noopener noreferrer"
              className="ml-1 text-muted-foreground transition-colors hover:text-foreground"
            >
              <Github className="size-[18px]" />
            </Link>
            <ThemeToggle />
          </div>
          <Button
            variant="ghost"
            size="icon"
            onClick={() => toggle(false)}
            className="size-9 text-muted-foreground hover:text-foreground"
          >
            <X className="size-5" />
          </Button>
        </div>

        {/* Links + CTA */}
        <motion.nav
          variants={staggerContainer}
          initial="initial"
          animate="animate"
          exit="exit"
          className="px-6 pt-4 pb-10"
        >
          <div className="ml-2">
            {links.map((link, index) => (
              <MobileNavLink
                key={`mobile-link-${index}`}
                toggle={toggle}
                label={link.label}
                href={link.href}
                external={link.external}
                shouldCloseOnClick={link.shouldCloseOnClick}
              />
            ))}
            <MobileNavLink
              key={`mobile-link-cta`}
              toggle={toggle}
              label={'Join the waitlist'}
              href="/#waitlist"
              external={false}
              shouldCloseOnClick={true}
            />
          </div>
        </motion.nav>
      </motion.div>
    </motion.div>
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
    <motion.div variants={linkBlockVars}>
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
    </motion.div>
  );
};
