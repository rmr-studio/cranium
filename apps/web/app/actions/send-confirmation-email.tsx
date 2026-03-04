'use server';

import { WaitlistConfirmation } from '@/emails/waitlist-confirmation';
import { Resend } from 'resend';

// Module-scope instantiation = fail-fast if API key is missing at startup
const resend = new Resend(process.env.RESEND_API_KEY);

export async function sendConfirmationEmail(
  name: string,
  email: string,
): Promise<{ success: boolean; id?: string; error?: string }> {
  try {
    const { data, error } = await resend.emails.send({
      from: 'Jared from Riven <jared@riven.software>',
      to: [email],
      bcc: ['jared@riven.software'],
      subject: `Hey ${name} \u2014 Thanks for joining the waitlist!`,
      react: <WaitlistConfirmation name={name} />,
    });

    if (error) {
      console.error('[sendConfirmationEmail] Resend error:', error);
      return { success: false, error: error.message };
    }

    return { success: true, id: data?.id };
  } catch (err) {
    const message = err instanceof Error ? err.message : 'Unknown error';
    console.error('[sendConfirmationEmail] Unexpected error:', message);
    return { success: false, error: message };
  }
}
