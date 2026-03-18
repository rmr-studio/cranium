'use client';

import { useAuth } from '@/components/provider/auth-context';
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from '@/components/ui/form';
import { getAuthErrorMessage, isAuthError, OAuthProvider } from '@/lib/auth';
import { zodResolver } from '@hookform/resolvers/zod';
import { Button } from '@riven/ui/button';
import { CardContent, CardHeader } from '@riven/ui/card';
import { Input } from '@riven/ui/input';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { FC } from 'react';
import { useForm, UseFormReturn } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import ThirdParty from './third-party';

const loginSchema = z.object({
  email: z.string().email('Invalid Email').min(1, 'Email is required'),
  password: z.string().min(1, 'Password is required'),
});

type Login = z.infer<typeof loginSchema>;

const LoginForm: FC = () => {
  const router = useRouter();
  const { signIn, signInWithOAuth } = useAuth();

  const loginForm: UseFormReturn<Login> = useForm<Login>({
    resolver: zodResolver(loginSchema),
    defaultValues: {
      email: '',
      password: '',
    },
  });

  const authenticateWithSocialProvider = async (provider: OAuthProvider): Promise<void> => {
    try {
      await signInWithOAuth(provider, {
        redirectTo: `${process.env.NEXT_PUBLIC_HOSTED_URL}api/auth/token/callback`,
      });
      // Note: signInWithOAuth handles the redirect internally
    } catch (error) {
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        toast.error('OAuth sign-in failed');
      }
    }
  };

  const handleLoginSubmission = async (values: Login) => {
    try {
      await signIn({ type: 'password', email: values.email, password: values.password });
      toast.success('Logged in successfully');
      router.push('/dashboard/workspace');
    } catch (error) {
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        toast.error('An unexpected error occurred');
      }
    }
  };

  return (
    <>
      <CardHeader className="pb-0">
        <h1 className="text-xl font-semibold lg:text-2xl">Welcome Back</h1>
        <h2 className="text-neutral-500 dark:text-neutral-400">Login into your account</h2>
      </CardHeader>
      <CardContent>
        <Form {...loginForm}>
          <form
            className="w-full md:w-[25rem]"
            onSubmit={loginForm.handleSubmit(handleLoginSubmission)}
          >
            <FormField
              control={loginForm.control}
              name="email"
              render={({ field }) => {
                return (
                  <FormItem>
                    <FormLabel className="mt-4">Email</FormLabel>
                    <FormControl>
                      <Input className="my-2 w-full" {...field} placeholder="name@example.com" />
                    </FormControl>
                    <FormMessage className="font-semibold" />
                  </FormItem>
                );
              }}
            />
            <FormField
              control={loginForm.control}
              name="password"
              render={({ field }) => {
                return (
                  <FormItem className="mt-4">
                    <FormLabel>Password</FormLabel>
                    <FormControl>
                      <Input
                        className="my-2 w-full"
                        type="password"
                        placeholder="••••••••••"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage className="font-semibold" />
                  </FormItem>
                );
              }}
            />

            <Button type="submit" className="mt-8 w-full font-semibold">
              Log In
            </Button>
          </form>
        </Form>
        <ThirdParty
          socialProviderAuthentication={authenticateWithSocialProvider}
          className="my-6"
        />
        <section className="mx-2 my-4 text-sm text-neutral-700 dark:text-neutral-400">
          <span>Not with us already?</span>
          <Link
            href={'/auth/register'}
            className="ml-2 text-neutral-800 underline hover:text-neutral-600 dark:text-neutral-200 dark:hover:text-neutral-400"
          >
            Sign Up
          </Link>
        </section>
      </CardContent>
    </>
  );
};

export default LoginForm;
