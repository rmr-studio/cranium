import { Faq } from '@/components/feature-modules/landing/faq/components/faq';
import { Waitlist } from '@/components/feature-modules/waitlist/components/waitlist';

export default function FaqPage() {
  return (
    <main className="mt-18 min-h-svh overflow-x-clip">
      <Faq />
      <Waitlist />
    </main>
  );
}
