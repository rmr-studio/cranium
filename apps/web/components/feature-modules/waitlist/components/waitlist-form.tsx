'use client';

import { sendSlackNotification } from '@/app/actions/send-slack-notification';
import { OkButton } from '@/components/feature-modules/waitlist/components/ok-button';
import { useWaitlistJoinMutation } from '@/hooks/use-waitlist-mutation';
import { cn } from '@/lib/utils';
import { waitlistJoinSchema, type WaitlistJoinData } from '@/lib/validations';
import { zodResolver } from '@hookform/resolvers/zod';
import { CheckCircle2 } from 'lucide-react';
import { AnimatePresence, motion } from 'motion/react';
import posthog from 'posthog-js';
import { useRef, useState } from 'react';
import { useForm } from 'react-hook-form';

const INPUT_CLASS =
  'w-full bg-transparent border-0 border-b border-foreground/20 pb-2 text-lg placeholder:text-muted-foreground/50 focus:outline-none focus:border-foreground/50 transition-colors';

const INPUT_ERROR_CLASS = 'border-destructive focus:border-destructive';

export function WaitlistForm({ className }: { className?: string }) {
  const [submitted, setSubmitted] = useState(false);
  const joinMutation = useWaitlistJoinMutation();
  const emailRef = useRef<HTMLInputElement | null>(null);

  const form = useForm<WaitlistJoinData>({
    resolver: zodResolver(waitlistJoinSchema),
    defaultValues: { name: '', email: '' },
    mode: 'onTouched',
  });

  const { register, handleSubmit, setError, formState } = form;
  const { ref: emailRegRef, ...emailRest } = register('email');

  const onSubmit = handleSubmit((data) => {
    joinMutation.mutate(data, {
      onSuccess: () => {
        posthog.capture('waitlist_joined', { email: data.email });
        sendSlackNotification(data).catch((err: Error) => {
          posthog.capture('slack_notification_failed', { error: err.message });
        });
        setSubmitted(true);
      },
      onError: (error: Error) => {
        if (error.message.includes('already on the waitlist')) {
          setError('email', { message: error.message });
        }
      },
    });
  });

  return (
    <div className={cn('mx-auto w-full max-w-5xl', className)}>
      <AnimatePresence mode="wait">
        {submitted ? (
          <motion.div
            key="success"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            className="py-16 text-center md:py-24"
          >
            <motion.div
              initial={{ scale: 0 }}
              animate={{ scale: 1 }}
              transition={{ type: 'spring', stiffness: 200, damping: 15, delay: 0.1 }}
            >
              <CheckCircle2 className="mx-auto h-14 w-14 text-teal-500" />
            </motion.div>
            <h3 className="mt-6 text-2xl font-normal text-heading md:text-3xl">
              You are on the list!
            </h3>
            <p className="mx-auto mt-3 max-w-md text-muted-foreground">
              Thanks for joining the waitlist. We can&apos;t wait to share updates and exciting
              features with you.
            </p>
          </motion.div>
        ) : (
          <motion.form
            key="form"
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0 }}
            onSubmit={onSubmit}
            className="py-8"
          >
            <h2 className="text-center font-bit text-4xl leading-none tracking-tighter text-heading md:text-6xl">
              Join the Waitlist
            </h2>
            <p className="mx-auto mt-5 mb-10 max-w-2xl px-4 text-center leading-tight tracking-tight text-muted-foreground sm:text-lg">
              The fastest way to see how Cranium transforms your e-commerce operations is to see how
              it fits into your business. Join the waitlist, and be the firs
            </p>

            <div className="mx-auto max-w-md space-y-6">
              <div>
                <label className="mb-1.5 block text-sm text-muted-foreground">Your name</label>
                <input
                  {...register('name')}
                  placeholder="Jane Doe"
                  onKeyDown={(e) => {
                    if (e.key === 'Enter') {
                      e.preventDefault();
                      emailRef.current?.focus();
                    }
                  }}
                  className={cn(INPUT_CLASS, formState.errors.name && INPUT_ERROR_CLASS)}
                />
                {formState.errors.name && (
                  <p className="mt-1.5 text-xs text-destructive">{formState.errors.name.message}</p>
                )}
              </div>

              <div>
                <label className="mb-1.5 block text-sm text-muted-foreground">Your email</label>
                <input
                  {...emailRest}
                  ref={(el) => {
                    emailRegRef(el);
                    emailRef.current = el;
                  }}
                  type="email"
                  placeholder="jane@company.com"
                  className={cn(INPUT_CLASS, formState.errors.email && INPUT_ERROR_CLASS)}
                />
                {formState.errors.email && (
                  <p className="mt-1.5 text-xs text-destructive">
                    {formState.errors.email.message}
                  </p>
                )}
              </div>

              <div className="mt-8 flex items-center justify-end">
                <OkButton
                  onClick={onSubmit}
                  label="Join the Waitlist"
                  loading={joinMutation.isPending}
                />
              </div>
            </div>
          </motion.form>
        )}
      </AnimatePresence>
    </div>
  );
}
