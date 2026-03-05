'use client';

import { Button, CardContent, CardHeader, Input } from '@riven/ui';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { OAuthProvider } from '@/lib/auth';
import Link from 'next/link';
import { FC, useState } from 'react';
import { UseFormReturn } from 'react-hook-form';
import { PasswordRequirements } from './PasswordRequirements';
import { Registration } from './Register';
import ThirdParty from './ThirdPartyAuth';

interface CredentialRegistrationProps {
  registrationForm: UseFormReturn<Registration>;
  handleSubmission: (values: Registration) => void;
  socialProviderAuthentication: (provider: OAuthProvider) => Promise<void>;
}

const RegisterCredentials: FC<CredentialRegistrationProps> = ({
  registrationForm,
  handleSubmission,
  socialProviderAuthentication,
}) => {
  const [passwordRequirementsVisible, setPasswordRequirementsVisible] = useState<boolean>(false);

  return (
    <>
      <CardHeader className="pb-0">
        <h1 className="text-xl font-semibold lg:text-2xl">Let{"'"}s Get Started</h1>
        <h2 className="text-neutral-500 dark:text-neutral-400">Create a new account</h2>
      </CardHeader>
      <CardContent>
        <Form {...registrationForm}>
          <form className="md:w-[25rem]" onSubmit={registrationForm.handleSubmit(handleSubmission)}>
            <FormField
              control={registrationForm.control}
              name="email"
              render={({ field }) => (
                <FormItem>
                  <FormLabel className="mt-4">Email</FormLabel>
                  <FormControl>
                    <Input className="my-2 w-full" {...field} placeholder="name@example.com" />
                  </FormControl>
                  <FormMessage className="font-semibold" />
                </FormItem>
              )}
            />

            <FormField
              control={registrationForm.control}
              name="password"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>Password</FormLabel>
                  <FormControl>
                    <Input
                      onFocus={() => setPasswordRequirementsVisible(true)}
                      className="my-2 w-full"
                      type="password"
                      {...field}
                      placeholder="••••••••••"
                    />
                  </FormControl>
                  <FormMessage className="font-semibold" />
                </FormItem>
              )}
            />
            <FormField
              control={registrationForm.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <FormLabel>Confirm Password</FormLabel>
                  <FormControl>
                    <Input
                      className="my-2 w-full"
                      type="password"
                      {...field}
                      placeholder="••••••••••"
                    />
                  </FormControl>
                  <FormMessage className="font-semibold" />
                </FormItem>
              )}
            />
            <PasswordRequirements
              control={registrationForm.control}
              visible={passwordRequirementsVisible}
            />
            <Button type="submit" className="mt-8 w-full font-semibold">
              Sign Up
            </Button>
          </form>
        </Form>

        <ThirdParty socialProviderAuthentication={socialProviderAuthentication} className="my-6" />
        <section className="mx-2 my-4 text-sm text-neutral-700 dark:text-neutral-400">
          <span>Already have an account?</span>
          <Link
            href={'/auth/login'}
            className="ml-2 text-neutral-800 underline hover:text-neutral-600 dark:text-neutral-200 dark:hover:text-neutral-400"
          >
            Sign In
          </Link>
        </section>
      </CardContent>
    </>
  );
};

export default RegisterCredentials;
