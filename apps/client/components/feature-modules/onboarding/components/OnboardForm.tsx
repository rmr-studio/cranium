import { useProfile } from '@/components/feature-modules/user/hooks/useProfile';
import type { User } from '@/lib/types/user';
import { updateUser } from '@/components/feature-modules/user/service/user.service';
import { useAuth } from '@/components/provider/auth-context';
import { Button, Input } from '@riven/ui';
import { Form, FormControl, FormField, FormItem, FormLabel } from '@/components/ui/form';
import { SheetDescription, SheetFooter, SheetTitle } from '@/components/ui/sheet';
import { Propless } from '@/lib/interfaces/interface';
import { zodResolver } from '@hookform/resolvers/zod';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { FC, useRef, useState } from 'react';
import { useForm, UseFormReturn } from 'react-hook-form';
import { toast } from 'sonner';
import { isMobilePhone } from 'validator';

import { AvatarUploader } from '@/components/ui/AvatarUploader';
import { PhoneInput } from '@/components/ui/phone-input';
import { TextSeparator } from '@/components/ui/text-separator';
import { OTPFormSchema } from '@/lib/util/form/form.util';
import { z } from 'zod';

// Avatar blob is sent to the backend. So is not included in form
const userOnboardDetailsSchema = z.object({
  displayName: z
    .string({ required_error: 'Display Name is required' })
    .min(3, 'Display Name is too short'),
  phone: z
    .string()
    .min(10, 'Invalid Phone Number')
    .refine(isMobilePhone)
    .optional()
    .or(z.literal('')),

  // OTP is only required if phone number is provided
  otp: OTPFormSchema.shape.otp.or(z.literal('')),
});

export type UserOnboard = z.infer<typeof userOnboardDetailsSchema>;

export const OnboardForm: FC<Propless> = () => {
  const { session } = useAuth();
  const { data: user } = useProfile();
  const queryClient = useQueryClient();
  const [uploadedAvatar, setUploadedAvatar] = useState<Blob | null>(null);
  const [currentAvatar, setCurrentAvatar] = useState<string | undefined>(user?.avatarUrl);

  const toastRef = useRef<string | number | null>(null);

  const form: UseFormReturn<UserOnboard> = useForm<UserOnboard>({
    resolver: zodResolver(userOnboardDetailsSchema),
    defaultValues: {
      displayName: user?.name || '',
      phone: user?.phone || undefined,
      otp: '',
    },
  });

  const handleAvatarRemoval = () => {
    setUploadedAvatar(null);
    setCurrentAvatar(user?.avatarUrl);
  };

  const userMutation = useMutation({
    mutationFn: (user: User) => updateUser(session, user, uploadedAvatar),
    onMutate: () => {
      toastRef.current = toast.loading('Updating Profile...');
    },
    onError: (error: Error) => {
      console.error('Error updating user profile:', error);
      if (toastRef.current !== null) {
        toast.dismiss(toastRef.current);
        toastRef.current = null;
      }

      toast.error('Failed to update Profile');
    },
    onSuccess: (response: User) => {
      if (toastRef.current !== null) {
        toast.dismiss(toastRef.current);
        toastRef.current = null;
      }

      toast.success('Profile Updated Successfully');
      // Update profile cache
      queryClient.setQueryData(['userProfile', session?.user.id], response);
    },
  });

  const handleSubmission = async (values: UserOnboard) => {
    if (!user || !session) return;
    // Update User Profile
    const updatedUser: User = {
      ...user,
      phone: values.phone,
      name: values.displayName,
    };

    userMutation.mutate(updatedUser);
  };

  const handleAvatarUpload = (file: Blob) => {
    setUploadedAvatar(file);
    setCurrentAvatar(URL.createObjectURL(file));
  };

  return (
    <Form {...form}>
      <form onSubmit={form.handleSubmit(handleSubmission)}>
        <SheetTitle className="mt-2 text-2xl font-bold">Complete your profile</SheetTitle>
        <SheetDescription className="mt-2">
          Please fill out the details below to complete your profile.
        </SheetDescription>
        <div className="mt-4 text-sm text-muted-foreground italic">
          <span className="font-semibold">Note:</span> You can update these details later in your
          profile settings.
        </div>
        <section className="my-4">
          <TextSeparator>
            <span className="text-[1rem] leading-1 font-semibold">Your details</span>
          </TextSeparator>
          <div className="flex flex-col md:space-x-4 lg:flex-row">
            <FormLabel className="pb-0 font-semibold md:hidden">Profile Picture</FormLabel>
            <AvatarUploader
              imageURL={currentAvatar}
              onUpload={handleAvatarUpload}
              onRemove={handleAvatarRemoval}
              validation={{
                allowedTypes: ['image/jpeg', 'image/png', 'image/gif'],
                maxSize: 2 * 1024 * 1024, // 2 MB
                errorMessage: 'Please upload a valid image (JPEG, PNG, GIF) under 2MB.',
              }}
            />
            <FormField
              control={form.control}
              name="displayName"
              render={({ field }) => (
                <FormItem className="mt-6 w-full lg:w-3/5">
                  <FormLabel className="font-semibold">Display Name *</FormLabel>
                  <FormControl>
                    <Input {...field} placeholder="John Doe" />
                  </FormControl>
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="phone"
              render={({ field }) => (
                <FormItem className="mt-6 w-full lg:w-2/5">
                  <FormLabel className="font-semibold">Phone *</FormLabel>
                  <FormControl>
                    <PhoneInput {...field} placeholder="0455 555 555" defaultCountry="AU" />
                  </FormControl>
                </FormItem>
              )}
            />
          </div>
        </section>

        <SheetFooter className="flex flex-row justify-end px-0">
          <Button className="w-32 cursor-pointer" type="submit">
            Save Changes
          </Button>
        </SheetFooter>
      </form>
    </Form>
  );
};
