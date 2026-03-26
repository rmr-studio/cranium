'use client';

import { RegistrationConfirmation } from '@/components/feature-modules/authentication/interface/auth.interface';
import { useAuth } from '@/components/provider/auth-context';
import { getAuthErrorMessage, isAuthError, OAuthProvider } from '@/lib/auth';
import { zodResolver } from '@hookform/resolvers/zod';
import { FC, useState } from 'react';
import { useForm, UseFormReturn } from 'react-hook-form';
import { toast } from 'sonner';
import { z } from 'zod';
import RegisterConfirmation from '@/components/feature-modules/authentication/components/register-confirmation';
import RegisterCredentials from '@/components/feature-modules/authentication/components/register-credentials';

// Authentication Form Schemas
const registrationSchema = z
  .object({
    email: z.string().email('Invalid email address').nonempty('Email is required'),
    password: z
      .string()
      .min(8, 'Password must be at least 8 characters long')
      .regex(/[A-Z]/, 'Password must contain at least one uppercase letter')
      .regex(/[a-z]/, 'Password must contain at least one lowercase letter')
      .regex(/\d/, 'Password must contain at least one digit')
      .regex(/[!@#$%^&*(),.?":{}|<>]/, 'Password must contain at least one special character')
      .nonempty('Password is required'),
    confirmPassword: z.string().min(1, 'Password confirmation is required'),
  })
  .superRefine(({ password, confirmPassword }, ctx) => {
    if (confirmPassword !== password) {
      ctx.addIssue({
        code: 'custom',
        message: 'The passwords did not match',
        path: ['confirmPassword'],
      });
    }
  });

export type Registration = z.infer<typeof registrationSchema>;

const RegisterForm: FC = () => {
  const registrationForm: UseFormReturn<Registration> = useForm<Registration>({
    resolver: zodResolver(registrationSchema),
    defaultValues: {
      email: '',
      password: '',
      confirmPassword: '',
    },
  });

  const [accountCreated, setAccountCreated] = useState<boolean>(false);
  const { signIn, signUp, verifyOtp, resendOtp, signInWithOAuth } = useAuth();

  const confirmEmailSignupWithOTP = async (
    userDetails: RegistrationConfirmation,
  ): Promise<void> => {
    const { otp, email, password } = userDetails;

    // Verify the OTP
    await verifyOtp({
      email,
      token: otp,
      type: 'signup',
    });

    // Auto sign-in after verification
    await signIn({ type: 'password', email, password });
  };

  const registerWithEmailPasswordCredentials = async (
    email: string,
    password: string,
  ): Promise<boolean> => {
    // signUp returns a result indicating auth status or confirmation requirement
    const result = await signUp({ email, password }, { confirmationType: 'otp' });

    // Return true if confirmation is required (user needs to enter OTP)
    // Return false if user is already authenticated (no confirmation needed)
    return result.status === 'confirmation_required';
  };

  const handleResendOTP = async (email: string): Promise<void> => {
    await resendOtp({
      email,
      type: 'signup',
    });
  };

  const authenticateWithSocialProvider = async (provider: OAuthProvider): Promise<void> => {
    try {
      await signInWithOAuth(provider, {
        redirectTo: `${process.env.NEXT_PUBLIC_HOSTED_URL}api/auth/token/callback`,
      });
    } catch (error) {
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        toast.error('OAuth sign-in failed');
      }
    }
  };

  const handleSubmission = async (values: Registration) => {
    const { email, password } = values;

    try {
      const requiresConfirmation = await registerWithEmailPasswordCredentials(email, password);

      if (requiresConfirmation) {
        // User needs to confirm email via OTP
        toast.success('Please check your email for a confirmation code');
        setAccountCreated(true);
      } else {
        // User is already authenticated (email confirmation disabled)
        toast.success('Account Created Successfully');
        // Note: Auth state change will trigger navigation via context
      }
    } catch (error) {
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        toast.error('Failed to create account');
      }
    }
  };

  return !accountCreated ? (
    <RegisterCredentials
      socialProviderAuthentication={authenticateWithSocialProvider}
      registrationForm={registrationForm}
      handleSubmission={handleSubmission}
    />
  ) : (
    <RegisterConfirmation
      confirmEmailSignupWithOTP={confirmEmailSignupWithOTP}
      handleResendOTP={handleResendOTP}
      visibilityCallback={setAccountCreated}
      formControl={registrationForm.control}
    />
  );
};

export default RegisterForm;
