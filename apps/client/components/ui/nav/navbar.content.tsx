'use client';

import type { User } from "@/lib/types/user";
import { useAuth } from "@/components/provider/auth-context";
import { FCWC, Propless } from "@/lib/interfaces/interface";
import { UseQueryResult } from "@tanstack/react-query";
import Link from "next/link";
import { FC } from "react";
import { Logo } from "@riven/ui/logo";
import { UserProfileDropdown } from "../../feature-modules/user/components/avatar-dropdown";
import { Button } from "../button";
import { Skeleton } from "../skeleton";
import { ThemeToggle } from "@riven/ui/theme-toggle";

interface UserProps {
  user: User;
}

export const NavbarUserProfile: FC<UseQueryResult<User>> = ({
  data: user,
  isLoading: loadingProfile,
}) => {
  const { loading: loadingAuth } = useAuth();

  if (loadingAuth || loadingProfile) return <Skeleton className="size-8 rounded-md" />;
  if (!user) return <UnauthenticatedNavbarProfile />;
  return <AuthenticatedNavbarProfile user={user} />;
};

export const AuthenticatedNavbarProfile: FC<UserProps> = ({ user }) => {
  return <UserProfileDropdown user={user} />;
};

export const UnauthenticatedNavbarProfile: FC<Propless> = () => {
  return (
    <div className="flex">
      <Button variant={'outline'}>
        <Link href="/auth/login">Login</Link>
      </Button>
      <Button className="ml-2">
        <Link href="/auth/register">Get Started</Link>
      </Button>
    </div>
  );
};

export const NavbarWrapper: FCWC<Propless> = ({ children }) => {
  return (
    <div className="sticky top-0 flex h-[4rem] w-auto flex-grow items-center border-b bg-background/40 px-4 backdrop-blur-[4px]">
      {children}
      <div className="flex items-center">
        <ThemeToggle />
      </div>
    </div>
  );
};

interface LogoProps {
  href: string;
}

export const NavbarLogo: FC<LogoProps> = ({ href }) => {
  return (
    <Link href={href} className="flex shrink-0 gap-1 px-3 md:px-4">
      <Logo size={32} />
      <div className="mt-1 font-serif text-2xl font-bold text-logo-primary">Riven</div>
    </Link>
  );
};
