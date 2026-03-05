'use client';

import { RegistrationConfirmation } from '@/components/feature-modules/authentication/interface/auth.interface';
import { Button, CardContent, CardHeader, Separator } from '@riven/ui';
import { Form, FormField, FormItem, FormMessage } from '@/components/ui/form';
import { FormOTPInput } from '@/components/ui/forms/form-otp-input';
import { getAuthErrorMessage, isAuthError } from '@/lib/auth';
import { FormOTP, OTPFormSchema } from '@/lib/util/form/form.util';
import { zodResolver } from '@hookform/resolvers/zod';
import { ArrowLeft } from 'lucide-react';
import { useRouter } from 'next/navigation';
import { FC, useState } from 'react';
import { Control, useForm, useWatch } from 'react-hook-form';
import { toast } from 'sonner';
import { Registration } from './Register';

interface RegisterConfirmationProps {
  confirmEmailSignupWithOTP: (userDetails: RegistrationConfirmation) => Promise<void>;
  handleResendOTP: (email: string) => Promise<void>;
  visibilityCallback: (visible: boolean) => void;
  formControl: Control<Registration>;
}

const RegisterConfirmation: FC<RegisterConfirmationProps> = ({
  formControl,
  confirmEmailSignupWithOTP,
  handleResendOTP,
  visibilityCallback,
}) => {
  const [otpVerifyError, setOtpVerifyError] = useState<boolean>(false);
  const formDetails = useWatch({ control: formControl });
  const userDetailsForm = useForm<FormOTP>({
    resolver: zodResolver(OTPFormSchema),
    defaultValues: {
      otp: '',
    },
  });

  const router = useRouter();

  const handleCancel = (): void => {
    userDetailsForm.reset();
    visibilityCallback(false);
  };

  const handleTokenResend = async (): Promise<void> => {
    if (!formDetails.email) return;

    const resendToast = toast.loading('Resending OTP...');

    try {
      await handleResendOTP(formDetails.email);
      toast.dismiss(resendToast);
      toast.success('OTP Sent Successfully');
    } catch (error) {
      toast.dismiss(resendToast);
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        toast.error('Failed to resend OTP');
      }
    }
  };

  const handleSubmission = async (values: FormOTP): Promise<void> => {
    if (!formDetails.email || !formDetails.password) return;

    setOtpVerifyError(false);
    const credentialValidation: RegistrationConfirmation = {
      email: formDetails.email,
      password: formDetails.password,
      otp: values.otp,
    };

    const verifyToast = toast.loading('Verifying OTP...');

    try {
      await confirmEmailSignupWithOTP(credentialValidation);
      toast.dismiss(verifyToast);
      toast.success('OTP Verified Successfully');
      router.push('/dashboard');
    } catch (error) {
      toast.dismiss(verifyToast);
      setOtpVerifyError(true);
      if (isAuthError(error)) {
        toast.error(getAuthErrorMessage(error.code));
      } else {
        toast.error('Failed to verify OTP');
      }
    }
  };

  return (
    <>
      <CardHeader className="pb-0">
        <h1 className="text-xl font-bold lg:text-2xl">Welcome aboard!</h1>
        <h2 className="max-w-sm font-semibold text-neutral-500 dark:text-neutral-400">
          Confirm your email to get your account started
        </h2>
      </CardHeader>
      <CardContent className="flex flex-col">
        <Form {...userDetailsForm}>
          <form onSubmit={userDetailsForm.handleSubmit(handleSubmission)}>
            <FormField
              control={userDetailsForm.control}
              name="otp"
              render={({ field }) => (
                <FormItem className="mt-4">
                  <div className="my-2 pb-2">
                    <FormOTPInput
                      className={otpVerifyError ? 'border-red-500' : 'border-secondary-foreground'}
                      regex="numeric"
                      field={field}
                      size={6}
                      groups={2}
                    />
                  </div>
                  <FormMessage className="font-semibold" />
                  <div className="w-full text-sm text-neutral-500 dark:text-neutral-400">
                    Please enter your 6 digit OTP sent to{' '}
                    <span className="font-semibold text-secondary-foreground">
                      {formDetails.email}
                    </span>
                  </div>
                  <Button
                    type="button"
                    onClick={handleTokenResend}
                    variant={'link'}
                    className="cursor-pointer p-0 text-sm text-muted-foreground underline underline-offset-4 hover:text-primary"
                  >
                    Didn't Receive an Email?
                  </Button>
                </FormItem>
              )}
            />
            <Separator className="my-4" />
            <footer className="mt-4 flex justify-between">
              <Button type="button" variant={'outline'} onClick={handleCancel}>
                <ArrowLeft className="h-4 w-4" />
                <span className="font-semibold">Back</span>
              </Button>
              <Button type="submit" className="px-8">
                Submit
              </Button>
            </footer>
          </form>
        </Form>
      </CardContent>
    </>
  );
};

export default RegisterConfirmation;
